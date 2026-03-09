/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package it.smartcommunitylabdhub.core.controllers.v1.context;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.schemas.Schema;
import it.smartcommunitylabdhub.commons.services.SchemaService;
import it.smartcommunitylabdhub.core.ApplicationKeys;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.dataitems.DataItem;
import it.smartcommunitylabdhub.dataitems.DataItemManager;
import it.smartcommunitylabdhub.dataitems.filters.DataItemEntityFilter;
import it.smartcommunitylabdhub.extensions.ExtensionManager;
import it.smartcommunitylabdhub.extensions.model.Extension;
import it.smartcommunitylabdhub.extensions.persistence.ExtensionBuilder;
import it.smartcommunitylabdhub.files.models.DownloadInfo;
import it.smartcommunitylabdhub.files.models.FileInfo;
import it.smartcommunitylabdhub.files.models.UploadInfo;
import it.smartcommunitylabdhub.files.service.EntityFilesService;
import it.smartcommunitylabdhub.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.relationships.RelationshipsAwareEntityService;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ApiVersion("v1")
@RequestMapping("/-/{project}/dataitems")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "DataItem context API", description = "Endpoints related to dataitems management in project")
public class DataItemContextController {

    @Autowired
    DataItemManager dataItemManager;

    @Autowired
    EntityFilesService<DataItem> filesService;

    @Autowired
    RelationshipsAwareEntityService<DataItem> relationshipsService;

    @Autowired
    ExtensionManager extensionManager;

    @Autowired
    SchemaService<DataItem> schemaService;

    @Operation(summary = "Create a dataItem in a project context")
    @PostMapping(
        value = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public DataItem createDataItem(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @Valid @NotNull @RequestBody DataItem dto
    ) throws DuplicatedEntityException, IllegalArgumentException, SystemException, BindException {
        //enforce project match
        dto.setProject(project);

        //create as new
        return dataItemManager.createDataItem(dto);
    }

    @Operation(summary = "Search dataItems")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<DataItem> searchDataItems(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @ParameterObject @Valid @Nullable DataItemEntityFilter filter,
        @ParameterObject @RequestParam(required = false, defaultValue = "latest") String versions,
        @ParameterObject @PageableDefault(page = 0, size = ApplicationKeys.DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "created", direction = Direction.DESC) }
        ) Pageable pageable
    ) {
        SearchFilter<DataItem> sf = null;
        if (filter != null) {
            sf = filter.toSearchFilter();
        }

        if ("all".equals(versions)) {
            return dataItemManager.searchDataItemsByProject(project, pageable, sf);
        } else {
            return dataItemManager.searchLatestDataItemsByProject(project, pageable, sf);
        }
    }

    @Operation(summary = "Delete all version of a dataItem")
    @DeleteMapping(path = "")
    public void deleteAllDataItem(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @ParameterObject @RequestParam @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String name,
        @RequestParam(required = false) Boolean cascade
    ) {
        dataItemManager.deleteDataItems(project, name, cascade);
    }

    /*
     * Versions
     */

    @Operation(summary = "Retrieve a specific dataItem version given the dataItem id")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public DataItem getDataItemById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        DataItem dataItem = dataItemManager.getDataItem(id);

        //check for project and name match
        if (!dataItem.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return dataItem;
    }

    @Operation(summary = "Update if exist a dataItem in a project context")
    @PutMapping(
        value = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public DataItem updateDataItemById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull DataItem dataItemDTO
    ) throws NoSuchEntityException, IllegalArgumentException, SystemException, BindException {
        DataItem dataItem = dataItemManager.getDataItem(id);

        //check for project and name match
        if (!dataItem.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return dataItemManager.updateDataItem(id, dataItemDTO);
    }

    @Operation(
        summary = "Delete a specific dataItem version",
        description = "First check if project exist, then delete a specific dataItem version"
    )
    @DeleteMapping(path = "/{id}")
    public void deleteDataItemById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam(required = false) Boolean cascade
    ) throws NoSuchEntityException {
        DataItem dataItem = dataItemManager.getDataItem(id);

        //check for project and name match
        if (!dataItem.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        dataItemManager.deleteDataItem(id, cascade);
    }

    /*
     * Files
     */
    @Operation(summary = "Get download url for a given entity, if available")
    @GetMapping(path = "/{id}/files/download", produces = "application/json; charset=UTF-8")
    public DownloadInfo downloadAsUrlById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @ParameterObject @RequestParam(required = false) String sub
    ) throws NoSuchEntityException {
        DataItem entity = dataItemManager.getDataItem(id);

        //check for project and name match
        if (!entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }
        if (sub != null) {
            return filesService.downloadFileAsUrl(id, sub);
        }

        return filesService.downloadFileAsUrl(id);
    }

    @Operation(summary = "Get download url for a given dataItem file, if available")
    @GetMapping(path = "/{id}/files/download/**", produces = "application/json; charset=UTF-8")
    public DownloadInfo downloadAsUrlFile(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        HttpServletRequest request
    ) throws NoSuchEntityException {
        DataItem dataItem = dataItemManager.getDataItem(id);

        //check for project and name match
        if (!dataItem.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        String path = request.getRequestURL().toString().split("files/download/")[1];
        return filesService.downloadFileAsUrl(id, path);
    }

    @Operation(summary = "Create an upload url for a given entity, if available")
    @PostMapping(path = "/{id}/files/upload", produces = "application/json; charset=UTF-8")
    public UploadInfo uploadAsUrlById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam(required = false) @Valid @Nullable @Pattern(regexp = Keys.SLUG_PATTERN) String name,
        @RequestParam @NotNull String filename
    ) throws NoSuchEntityException {
        DataItem entity = dataItemManager.findDataItem(id);

        //check for project and name match
        if (entity != null) {
            if (!entity.getProject().equals(project)) {
                throw new IllegalArgumentException("invalid project");
            }
            if ((name != null) && !entity.getName().equals(name)) {
                throw new IllegalArgumentException("invalid name");
            }
        }

        return filesService.uploadFileAsUrl(project, name, id, filename);
    }

    @Operation(summary = "Start a multipart upload for a given entity, if available")
    @PostMapping(path = "/{id}/files/multipart/start", produces = "application/json; charset=UTF-8")
    public UploadInfo multipartStartUploadAsUrlById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam(required = false) @Valid @Nullable @Pattern(regexp = Keys.SLUG_PATTERN) String name,
        @RequestParam @NotNull String filename
    ) throws NoSuchEntityException {
        DataItem entity = dataItemManager.findDataItem(id);

        //check for project and name match
        if (entity != null) {
            if (!entity.getProject().equals(project)) {
                throw new IllegalArgumentException("invalid project");
            }
            if ((name != null) && !entity.getName().equals(name)) {
                throw new IllegalArgumentException("invalid name");
            }
        }

        return filesService.startMultiPartUpload(project, name, id, filename);
    }

    @Operation(
        summary = "Generate an upload url for a part of a given multipart upload for a given entity, if available"
    )
    @PutMapping(path = "/{id}/files/multipart/part", produces = "application/json; charset=UTF-8")
    public UploadInfo multipartPartUploadAsUrlById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam @NotNull String path,
        @RequestParam @NotNull String uploadId,
        @RequestParam @NotNull Integer partNumber
    ) throws NoSuchEntityException {
        DataItem entity = dataItemManager.findDataItem(id);

        //check for project match
        if ((entity != null) && !entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return filesService.uploadMultiPart(project, id, path, uploadId, partNumber);
    }

    @Operation(summary = "Create a completing multipart upload url for a given entity, if available")
    @PostMapping(path = "/{id}/files/multipart/complete", produces = "application/json; charset=UTF-8")
    public UploadInfo multipartCompleteUploadAsUrlById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam @NotNull String path,
        @RequestParam @NotNull String uploadId,
        @RequestParam @NotNull List<String> partList
    ) throws NoSuchEntityException {
        DataItem entity = dataItemManager.findDataItem(id);

        //check for project match
        if ((entity != null) && !entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return filesService.completeMultiPartUpload(project, id, path, uploadId, partList);
    }

    @Operation(summary = "Get file info for a given entity, if available")
    @GetMapping(path = "/{id}/files/info", produces = "application/json; charset=UTF-8")
    public List<FileInfo> getFilesInfoById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        DataItem entity = dataItemManager.getDataItem(id);

        //check for project and name match
        if (!entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return filesService.getFileInfo(id);
    }

    @Operation(summary = "Store file info for a given entity, if available")
    @PutMapping(path = "/{id}/files/info", produces = "application/json; charset=UTF-8")
    public void storeFilesInfoById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody List<FileInfo> files
    ) throws NoSuchEntityException {
        DataItem entity = dataItemManager.getDataItem(id);

        //check for project and name match
        if (!entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        filesService.storeFileInfo(id, files);
    }

    @Operation(summary = "Get relationships info for a given entity, if available")
    @GetMapping(path = "/{id}/relationships", produces = "application/json; charset=UTF-8")
    public List<RelationshipDetail> getRelationshipsById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        DataItem entity = dataItemManager.getDataItem(id);

        //check for project and name match
        if ((entity != null) && !entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return relationshipsService.getRelationships(id);
    }

    @Operation(summary = "Get extensions for a given dataItem, if available")
    @GetMapping(path = "/{id}/extensions", produces = "application/json; charset=UTF-8")
    public List<Extension> getDataItemExtensionsById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        DataItem dataItem = dataItemManager.getDataItem(id);

        //check for project and name match
        if (!dataItem.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return extensionManager.listExtensionsByParent(ExtensionBuilder.from(dataItem).getParent());
    }

    @Operation(summary = "Store extensions for a given entity, if available")
    @PutMapping(path = "/{id}/extensions", produces = "application/json; charset=UTF-8")
    public void storeExtensionsById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody List<Extension> extensions
    ) throws NoSuchEntityException, IllegalArgumentException, SystemException, BindException {
        DataItem entity = dataItemManager.getDataItem(id);

        //check for project and name match
        if (!entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        if (extensions != null) {
            //enforce parent+project
            String parent = ExtensionBuilder.from(entity).getParent();
            List<Extension> previous = extensionManager.listExtensionsByParent(parent);
            List<Extension> updated = new ArrayList<>();

            //update or create as defined
            for (Extension ext : extensions) {
                ext.setParent(parent);
                ext.setProject(entity.getProject());
                Extension e = extensionManager.createOrUpdateExtension(ext);
                updated.add(e);
            }

            //delete removed
            List<Extension> toDelete = previous
                .stream()
                .filter(e -> updated.stream().noneMatch(u -> u.getId().equals(e.getId())))
                .toList();
            for (Extension ext : toDelete) {
                extensionManager.deleteExtension(ext.getId());
            }
        }
    }

    @Operation(
        summary = "List entity schemas",
        description = "Return a list of all the spec schemas available for the given entity"
    )
    @GetMapping(path = "/schemas")
    public ResponseEntity<Page<Schema>> listDataItemSchemas(
        @PathVariable @Valid @NotNull String project,
        @RequestParam(required = false) Optional<String> runtime,
        Pageable pageable
    ) {
        Collection<Schema> schemas = runtime.isPresent()
            ? schemaService.listSchemas(runtime.get())
            : schemaService.listSchemas();
        PageImpl<Schema> page = new PageImpl<>(new ArrayList<>(schemas), pageable, schemas.size());

        return ResponseEntity.ok(page);
    }

    @GetMapping(path = "/schemas/{kind}", produces = "application/json; charset=UTF-8")
    public ResponseEntity<Schema> getDataItemSchema(
        @PathVariable @Valid @NotNull String project,
        @PathVariable @NotBlank String kind
    ) {
        Schema schema = schemaService.getSchema(kind);

        return ResponseEntity.ok(schema);
    }
}
