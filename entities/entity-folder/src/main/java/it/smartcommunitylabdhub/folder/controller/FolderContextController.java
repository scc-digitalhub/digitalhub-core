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

package it.smartcommunitylabdhub.folder.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.schemas.Schema;
import it.smartcommunitylabdhub.commons.services.SchemaService;
import it.smartcommunitylabdhub.core.services.EntityService;
import it.smartcommunitylabdhub.files.models.TokenPageRequest;
import it.smartcommunitylabdhub.folder.Folder;
import it.smartcommunitylabdhub.folder.persistence.FolderEntry;
import it.smartcommunitylabdhub.folder.services.FolderEntriesService;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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
@RequestMapping("/api/v1/-/{project}/folders")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "Folder context API", description = "Endpoints related to folders management in project")
public class FolderContextController {

    public static final int DEFAULT_PAGE_SIZE = 25;

    @Autowired
    private EntityService<Folder> entityService;

    @Autowired
    private FolderEntriesService folderEntriesService;

    @Autowired
    SchemaService<Folder> schemaService;

    @Operation(summary = "Create an folder in a project context")
    @PostMapping(
        value = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Folder createFolder(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @Valid @NotNull @RequestBody Folder dto
    ) throws DuplicatedEntityException, IllegalArgumentException, SystemException, BindException, StoreException {
        //enforce project match
        dto.setProject(project);

        //create as new
        return entityService.create(dto);
    }

    @Operation(summary = "Search folders")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<Folder> searchFolders(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @ParameterObject @Valid @Nullable FolderEntityFilter filter,
        @ParameterObject @PageableDefault(page = 0, size = DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "created", direction = Direction.DESC) }
        ) Pageable pageable
    ) throws StoreException {
        SearchFilter<Folder> sf = null;
        if (filter != null) {
            sf = filter.toSearchFilter();
        }

        return entityService.searchByProject(project, sf, pageable);
    }

    @Operation(summary = "Retrieve a specific folder given the folder id")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public Folder getFolderById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException, StoreException {
        Folder folder = entityService.get(id);

        //check for project and name match
        if (!folder.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return folder;
    }

    @Operation(summary = "Update if exist an folder in a project context")
    @PutMapping(
        value = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Folder updateFolderById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull Folder folderDTO
    ) throws NoSuchEntityException, IllegalArgumentException, SystemException, BindException, StoreException {
        Folder folder = entityService.get(id);

        //check for project and name match
        if (!folder.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return entityService.update(id, folderDTO);
    }

    @Operation(summary = "Delete a specific folder")
    @DeleteMapping(path = "/{id}")
    public void deleteFolderById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam(required = false) Boolean cascade
    ) throws NoSuchEntityException, StoreException {
        Folder folder = entityService.get(id);

        //check for project and name match
        if (!folder.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        entityService.delete(id, cascade);
    }

    @Operation(
        summary = "List entity schemas",
        description = "Return a list of all the spec schemas available for the given entity"
    )
    @GetMapping(path = "/schemas")
    public ResponseEntity<Page<Schema>> listFolderSchemas(
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
    public ResponseEntity<Schema> getFolderSchema(
        @PathVariable @Valid @NotNull String project,
        @PathVariable @NotBlank String kind
    ) {
        Schema schema = schemaService.getSchema(kind);

        return ResponseEntity.ok(schema);
    }

    /*
     * Entries
     */
    @GetMapping(path = "/entries", produces = "application/json; charset=UTF-8")
    public Slice<FolderEntry> listEntries(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @ParameterObject TokenPageRequest pageable
    ) throws NoSuchEntityException, StoreException {
        return folderEntriesService.listEntries(project, null, pageable, pageable.getToken());
    }

    @GetMapping(path = "/{id}/entries", produces = "application/json; charset=UTF-8")
    public Slice<FolderEntry> listFolderEntries(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @ParameterObject TokenPageRequest pageable
    ) throws NoSuchEntityException, StoreException {
        Folder folder = entityService.get(id);

        //check for project and name match
        if (!folder.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return folderEntriesService.listEntries(project, id, pageable, pageable.getToken());
    }
}
