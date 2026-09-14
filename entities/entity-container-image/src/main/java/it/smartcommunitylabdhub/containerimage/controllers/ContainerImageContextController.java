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

package it.smartcommunitylabdhub.containerimage.controllers;

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
import it.smartcommunitylabdhub.containerimage.ContainerImage;
import it.smartcommunitylabdhub.containerimage.filter.ContainerImageEntityFilter;
import it.smartcommunitylabdhub.core.services.EntityService;
import it.smartcommunitylabdhub.core.services.VersionableEntityService;
import it.smartcommunitylabdhub.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.relationships.RelationshipsAwareEntityService;
import jakarta.annotation.Nullable;
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
@RequestMapping("/api/v1/-/{project}/containerimages")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "ContainerImage context API", description = "Endpoints related to containerImages management in project")
public class ContainerImageContextController {

    public static final int DEFAULT_PAGE_SIZE = 25;

    @Autowired
    private EntityService<ContainerImage> entityService;

    @Autowired
    private VersionableEntityService<ContainerImage> versionableService;

    @Autowired
    RelationshipsAwareEntityService<ContainerImage> relationshipsService;

    @Autowired
    SchemaService<ContainerImage> schemaService;

    @Operation(summary = "Create an containerImage in a project context")
    @PostMapping(
        value = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public ContainerImage createContainerImage(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @Valid @NotNull @RequestBody ContainerImage dto
    ) throws DuplicatedEntityException, IllegalArgumentException, SystemException, BindException, StoreException {
        //enforce project match
        dto.setProject(project);

        //create as new
        return entityService.create(dto);
    }

    @Operation(summary = "Search containerImages")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<ContainerImage> searchContainerImages(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @ParameterObject @Valid @Nullable ContainerImageEntityFilter filter,
        @ParameterObject @RequestParam(required = false, defaultValue = "latest") String versions,
        @ParameterObject @PageableDefault(page = 0, size = DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "created", direction = Direction.DESC) }
        ) Pageable pageable
    ) throws StoreException {
        SearchFilter<ContainerImage> sf = null;
        if (filter != null) {
            sf = filter.toSearchFilter();
        }
        if ("all".equals(versions)) {
            return entityService.searchByProject(project, sf, pageable);
        } else {
            return versionableService.searchLatestByProject(project, sf, pageable);
        }
    }

    @Operation(summary = "Delete all version of an containerImage")
    @DeleteMapping(path = "")
    public void deleteAllContainerImages(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @ParameterObject @RequestParam @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String name,
        @RequestParam(required = false) Boolean cascade
    ) throws StoreException {
        versionableService.deleteAll(project, name, cascade);
    }

    /*
     * Versions
     */

    @Operation(summary = "Retrieve a specific containerImage version given the containerImage id")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public ContainerImage getContainerImageById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException, StoreException {
        ContainerImage containerImage = entityService.get(id);

        //check for project and name match
        if (!containerImage.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return containerImage;
    }

    @Operation(summary = "Update if exist an containerImage in a project context")
    @PutMapping(
        value = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public ContainerImage updateContainerImageById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull ContainerImage containerImageDTO
    ) throws NoSuchEntityException, IllegalArgumentException, SystemException, BindException, StoreException {
        ContainerImage containerImage = entityService.get(id);

        //check for project and name match
        if (!containerImage.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return entityService.update(id, containerImageDTO);
    }

    @Operation(summary = "Delete a specific containerImage version")
    @DeleteMapping(path = "/{id}")
    public void deleteContainerImageById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam(required = false) Boolean cascade
    ) throws NoSuchEntityException, StoreException {
        ContainerImage containerImage = entityService.get(id);

        //check for project and name match
        if (!containerImage.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        entityService.delete(id, cascade);
    }

    @Operation(summary = "Get relationships info for a given entity, if available")
    @GetMapping(path = "/{id}/relationships", produces = "application/json; charset=UTF-8")
    public List<RelationshipDetail> getRelationshipsById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException, StoreException {
        ContainerImage entity = entityService.get(id);

        //check for project and name match
        if ((entity != null) && !entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return relationshipsService.getRelationships(id);
    }

    @Operation(
        summary = "List entity schemas",
        description = "Return a list of all the spec schemas available for the given entity"
    )
    @GetMapping(path = "/schemas")
    public ResponseEntity<Page<Schema>> listContainerImageSchemas(
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
    public ResponseEntity<Schema> getContainerImageSchema(
        @PathVariable @Valid @NotNull String project,
        @PathVariable @NotBlank String kind
    ) {
        Schema schema = schemaService.getSchema(kind);

        return ResponseEntity.ok(schema);
    }
}
