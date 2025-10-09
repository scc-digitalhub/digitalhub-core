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
import it.smartcommunitylabdhub.commons.models.artifact.Artifact;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.services.ArtifactManager;
import it.smartcommunitylabdhub.core.ApplicationKeys;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.artifacts.filters.ArtifactEntityFilter;
import it.smartcommunitylabdhub.files.models.DownloadInfo;
import it.smartcommunitylabdhub.files.models.FileInfo;
import it.smartcommunitylabdhub.files.models.UploadInfo;
import it.smartcommunitylabdhub.files.service.EntityFilesService;
import it.smartcommunitylabdhub.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.relationships.RelationshipsAwareEntityService;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.MediaType;
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
@RequestMapping("/-/{project}/artifacts")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "Artifact context API", description = "Endpoints related to artifacts management in project")
public class ArtifactContextController {

    @Autowired
    ArtifactManager artifactManager;

    @Autowired
    EntityFilesService<Artifact> filesService;

    @Autowired
    RelationshipsAwareEntityService<Artifact> relationshipsService;

    @Operation(summary = "Create an artifact in a project context")
    @PostMapping(
        value = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Artifact createArtifact(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @Valid @NotNull @RequestBody Artifact dto
    ) throws DuplicatedEntityException, IllegalArgumentException, SystemException, BindException {
        //enforce project match
        dto.setProject(project);

        //create as new
        return artifactManager.createArtifact(dto);
    }

    @Operation(summary = "Search artifacts")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<Artifact> searchArtifacts(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @ParameterObject @Valid @Nullable ArtifactEntityFilter filter,
        @ParameterObject @RequestParam(required = false, defaultValue = "latest") String versions,
        @ParameterObject @PageableDefault(page = 0, size = ApplicationKeys.DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "created", direction = Direction.DESC) }
        ) Pageable pageable
    ) {
        SearchFilter<Artifact> sf = null;
        if (filter != null) {
            sf = filter.toSearchFilter();
        }
        if ("all".equals(versions)) {
            return artifactManager.searchArtifactsByProject(project, pageable, sf);
        } else {
            return artifactManager.searchLatestArtifactsByProject(project, pageable, sf);
        }
    }

    @Operation(summary = "Delete all version of an artifact")
    @DeleteMapping(path = "")
    public void deleteAllArtifacts(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @ParameterObject @RequestParam @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String name,
        @RequestParam(required = false) Boolean cascade
    ) {
        artifactManager.deleteArtifacts(project, name, cascade);
    }

    /*
     * Versions
     */

    @Operation(summary = "Retrieve a specific artifact version given the artifact id")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public Artifact getArtifactById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Artifact artifact = artifactManager.getArtifact(id);

        //check for project and name match
        if (!artifact.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return artifact;
    }

    @Operation(summary = "Update if exist an artifact in a project context")
    @PutMapping(
        value = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Artifact updateArtifactById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull Artifact artifactDTO
    ) throws NoSuchEntityException, IllegalArgumentException, SystemException, BindException {
        Artifact artifact = artifactManager.getArtifact(id);

        //check for project and name match
        if (!artifact.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return artifactManager.updateArtifact(id, artifactDTO);
    }

    @Operation(summary = "Delete a specific artifact version")
    @DeleteMapping(path = "/{id}")
    public void deleteArtifactById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam(required = false) Boolean cascade
    ) throws NoSuchEntityException {
        Artifact artifact = artifactManager.getArtifact(id);

        //check for project and name match
        if (!artifact.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        artifactManager.deleteArtifact(id, cascade);
    }

    /*
     * Files
     */
    @Operation(summary = "Get download url for a given artifact, if available")
    @GetMapping(path = "/{id}/files/download", produces = "application/json; charset=UTF-8")
    public DownloadInfo downloadAsUrlArtifactById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @ParameterObject @RequestParam(required = false) String sub
    ) throws NoSuchEntityException {
        Artifact artifact = artifactManager.getArtifact(id);

        //check for project and name match
        if (!artifact.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        if (sub != null) {
            return filesService.downloadFileAsUrl(id, sub);
        }

        return filesService.downloadFileAsUrl(id);
    }

    @Operation(summary = "Get download url for a given artifact file, if available")
    @GetMapping(path = "/{id}/files/download/**", produces = "application/json; charset=UTF-8")
    public DownloadInfo downloadAsUrlFile(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        HttpServletRequest request
    ) throws NoSuchEntityException {
        Artifact artifact = artifactManager.getArtifact(id);

        //check for project and name match
        if (!artifact.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }
        String path = request.getRequestURL().toString().split("files/download/")[1];
        return filesService.downloadFileAsUrl(id, path);
    }

    @Operation(summary = "Create an upload url for a given artifact, if available")
    @PostMapping(path = "/{id}/files/upload", produces = "application/json; charset=UTF-8")
    public UploadInfo uploadAsUrlArtifactById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam(required = false) @Valid @Nullable @Pattern(regexp = Keys.SLUG_PATTERN) String name,
        @RequestParam @NotNull String filename
    ) throws NoSuchEntityException {
        Artifact artifact = artifactManager.findArtifact(id);

        //check for project and name match
        if (artifact != null) {
            if (!artifact.getProject().equals(project)) {
                throw new IllegalArgumentException("invalid project");
            }
            if ((name != null) && !artifact.getName().equals(name)) {
                throw new IllegalArgumentException("invalid name");
            }
        }

        return filesService.uploadFileAsUrl(project, name, id, filename);
    }

    @Operation(summary = "Start a multipart upload for a given artifact, if available")
    @PostMapping(path = "/{id}/files/multipart/start", produces = "application/json; charset=UTF-8")
    public UploadInfo multipartStartUploadAsUrlArtifactById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam(required = false) @Valid @Nullable @Pattern(regexp = Keys.SLUG_PATTERN) String name,
        @RequestParam @NotNull String filename
    ) throws NoSuchEntityException {
        Artifact artifact = artifactManager.findArtifact(id);

        //check for project and name match
        if (artifact != null) {
            if (!artifact.getProject().equals(project)) {
                throw new IllegalArgumentException("invalid project");
            }
            if ((name != null) && !artifact.getName().equals(name)) {
                throw new IllegalArgumentException("invalid name");
            }
        }

        return filesService.startMultiPartUpload(project, name, id, filename);
    }

    @Operation(
        summary = "Generate an upload url for a part of a given multipart upload for a given artifact, if available"
    )
    @PutMapping(path = "/{id}/files/multipart/part", produces = "application/json; charset=UTF-8")
    public UploadInfo multipartPartUploadAsUrlArtifactById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam @NotNull String path,
        @RequestParam @NotNull String uploadId,
        @RequestParam @NotNull Integer partNumber
    ) throws NoSuchEntityException {
        Artifact artifact = artifactManager.findArtifact(id);

        //check for project match
        if ((artifact != null) && !artifact.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return filesService.uploadMultiPart(project, id, path, uploadId, partNumber);
    }

    @Operation(summary = "Complete a multipart upload for a given artifact, if available")
    @PostMapping(path = "/{id}/files/multipart/complete", produces = "application/json; charset=UTF-8")
    public UploadInfo multipartCompleteUploadAsUrlArtifactById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam @NotNull String path,
        @RequestParam @NotNull String uploadId,
        @RequestParam @NotNull List<String> partList
    ) throws NoSuchEntityException {
        Artifact artifact = artifactManager.findArtifact(id);

        //check for project match
        if ((artifact != null) && !artifact.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return filesService.completeMultiPartUpload(project, id, path, uploadId, partList);
    }

    @Operation(summary = "Get file info for a given artifact, if available")
    @GetMapping(path = "/{id}/files/info", produces = "application/json; charset=UTF-8")
    public List<FileInfo> getArtifactFilesInfoById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Artifact artifact = artifactManager.getArtifact(id);

        //check for project and name match
        if (!artifact.getProject().equals(project)) {
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
        Artifact entity = artifactManager.getArtifact(id);

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
        Artifact entity = artifactManager.getArtifact(id);

        //check for project and name match
        if ((entity != null) && !entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return relationshipsService.getRelationships(id);
    }
}
