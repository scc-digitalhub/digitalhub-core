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
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.containerimage.ContainerImage;
import it.smartcommunitylabdhub.containerimage.filter.ContainerImageEntityFilter;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/containerimages")
@PreAuthorize("hasAuthority('ROLE_ADMIN') ")
@Validated
@Slf4j
@Tag(name = "ContainerImage  API", description = "Endpoints related to containerImages management")
public class ContainerImageController {

    public static final int DEFAULT_PAGE_SIZE = 25;

    @Autowired
    private EntityService<ContainerImage> entityService;

    @Autowired
    private VersionableEntityService<ContainerImage> versionableService;

    @Operation(summary = "Search containerImages")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<ContainerImage> searchContainerImages(
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
            return entityService.search(sf, pageable);
        } else {
            return versionableService.searchLatest(sf, pageable);
        }
    }

    /*
     * Versions
     */

    @Operation(summary = "Retrieve a specific containerImage version given the containerImage id")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public ContainerImage getContainerImageById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException, StoreException {
        return entityService.get(id);
    }

    @Operation(summary = "Update if exist an containerImage in a project context")
    @PutMapping(
        value = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public ContainerImage updateContainerImageById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull ContainerImage containerImageDTO
    ) throws NoSuchEntityException, IllegalArgumentException, SystemException, BindException, StoreException {
        return entityService.update(id, containerImageDTO);
    }

    @Operation(summary = "Delete a specific containerImage version")
    @DeleteMapping(path = "/{id}")
    public void deleteContainerImageById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam(required = false) Boolean cascade
    ) throws NoSuchEntityException, StoreException {
        entityService.delete(id, cascade);
    }

    @Operation(summary = "Delete all version of an containerImage")
    @DeleteMapping(path = "")
    public void deleteAllContainerImages(
        @ParameterObject @RequestParam @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @ParameterObject @RequestParam @Valid @NotNull @Pattern(regexp = Keys.NAME_PATTERN) String name,
        @RequestParam(required = false) Boolean cascade
    ) throws StoreException {
        for (ContainerImage containerImage : versionableService.findAll(project, name)) {
            entityService.delete(containerImage.getId(), cascade);
        }
    }
}
