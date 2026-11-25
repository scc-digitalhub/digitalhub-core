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

package it.smartcommunitylabdhub.core.controllers;

import io.swagger.v3.oas.annotations.Operation;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.core.queries.filters.AbstractEntityFilter;
import it.smartcommunitylabdhub.core.services.VersionableEntityService;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
public abstract class BaseEntityController<D extends BaseDTO> {

    public static final int DEFAULT_PAGE_SIZE = 25;

    protected EntityService<D> entityService;
    protected VersionableEntityService<D> versionableService;

    @Autowired
    public void setEntityService(EntityService<D> entityService) {
        this.entityService = entityService;
    }

    @Autowired(required = false)
    public void setVersionableService(VersionableEntityService<D> versionableService) {
        this.versionableService = versionableService;
    }

    @Operation(summary = "Create a dto in a project context")
    @PostMapping(
        value = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public D create(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @Valid @NotNull @RequestBody D dto
    ) throws DuplicatedEntityException, IllegalArgumentException, BindException, StoreException {
        //check for project
        if (!dto.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        //create as new
        return entityService.create(dto);
    }

    @Operation(summary = "Search dtos")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<D> search(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @ParameterObject @Valid @Nullable AbstractEntityFilter<D> filter,
        @ParameterObject @RequestParam(required = false, defaultValue = "latest") String versions,
        @ParameterObject @PageableDefault(page = 0, size = DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "created", direction = Direction.DESC) }
        ) Pageable pageable
    ) throws StoreException {
        SearchFilter<D> sf = null;
        if (filter != null) {
            sf = filter.toSearchFilter();
        }
        if (versionableService == null || "all".equals(versions)) {
            return entityService.searchByProject(project, sf, pageable);
        } else {
            return versionableService.searchLatestByProject(project, sf, pageable);
        }
    }

    @Operation(summary = "Delete all version of an dto")
    @DeleteMapping(path = "")
    public void deleteAll(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @ParameterObject @RequestParam @Valid @NotNull @Pattern(regexp = Keys.NAME_PATTERN) String name,
        @RequestParam(required = false) Boolean cascade
    ) throws StoreException {
        if (versionableService == null) {
            throw new UnsupportedOperationException("versionable service not available");
        }

        //delete all versions of the dto
        for (D dto : versionableService.findAll(project, name)) {
            entityService.delete(dto.getId(), cascade);
        }
    }

    /*
     * Versions
     */

    @Operation(summary = "Retrieve a specific dto version given the dto id")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public D getById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException, StoreException {
        D dto = entityService.get(id);

        //check for project and name match
        if (!dto.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return dto;
    }

    @Operation(summary = "Update if exist an dto in a project context")
    @PutMapping(
        value = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public D updateById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam(required = false) Boolean force,
        @RequestBody @Valid @NotNull D dtoDTO,
        Authentication authentication
    ) throws NoSuchEntityException, IllegalArgumentException, StoreException, BindException {
        D dto = entityService.get(id);

        //check for project and name match
        if (!dto.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        //business rule: only owner or admins can FORCE update
        boolean isForce = force != null && force.booleanValue();
        if (isForce) {
            //check if user is owner or admin
            if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
                throw new IllegalArgumentException("force update requires authentication");
            }

            if (
                !authentication.getName().equals(dto.getUser()) &&
                authentication
                    .getAuthorities()
                    .stream()
                    .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
            ) {
                throw new IllegalArgumentException("force update requires owner or admin authentication");
            }
        }

        return entityService.update(id, dtoDTO, isForce);
    }

    @Operation(summary = "Partially update if exist an dto in a project context")
    @PatchMapping(
        value = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public D patchById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam(required = false) Boolean force,
        @RequestBody @Valid @NotNull D dtoDTO,
        Authentication authentication
    ) throws NoSuchEntityException, IllegalArgumentException, StoreException, BindException {
        D dto = entityService.get(id);

        //check for project and name match
        if (!dto.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        //business rule: only owner or admins can FORCE update
        boolean isForce = force != null && force.booleanValue();
        if (isForce) {
            //check if user is owner or admin
            if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
                throw new IllegalArgumentException("force update requires authentication");
            }

            if (
                !authentication.getName().equals(dto.getUser()) &&
                authentication
                    .getAuthorities()
                    .stream()
                    .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
            ) {
                throw new IllegalArgumentException("force update requires owner or admin authentication");
            }
        }

        //perform partial update via merge update
        return entityService.update(id, dtoDTO, force != null && force.booleanValue(), true);
    }

    @Operation(summary = "Delete a specific dto version")
    @DeleteMapping(path = "/{id}")
    public void deleteById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam(required = false) Boolean cascade
    ) throws NoSuchEntityException, StoreException {
        D dto = entityService.get(id);

        //check for project and name match
        if (!dto.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        entityService.delete(id, cascade);
    }
}
