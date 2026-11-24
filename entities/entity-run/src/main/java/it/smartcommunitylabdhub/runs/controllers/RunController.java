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

package it.smartcommunitylabdhub.runs.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.lifecycle.LifecycleManager;
import it.smartcommunitylabdhub.metrics.ResourceMetrics;
import it.smartcommunitylabdhub.metrics.ResourceMetricsService;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runs.RunManager;
import it.smartcommunitylabdhub.runs.filters.RunEntityFilter;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/runs")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Validated
@Slf4j
@Tag(name = "Run base API", description = "Endpoints related to runs management out of the Context")
public class RunController {

    public static final int DEFAULT_PAGE_SIZE = 25;

    @Autowired
    RunManager runManager;

    @Autowired
    LifecycleManager<Run> lifecycleManager;

    @Autowired(required = false)
    private ResourceMetricsService resourceMetricsService;

    @Operation(summary = "List runs", description = "Return a list of all runs")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<Run> getRuns(
        @ParameterObject @Valid @Nullable RunEntityFilter filter,
        @ParameterObject @PageableDefault(page = 0, size = DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "created", direction = Direction.DESC) }
        ) Pageable pageable
    ) throws StoreException {
        SearchFilter<Run> sf = null;
        if (filter != null) {
            sf = filter.toSearchFilter();
        }

        return runManager.search(sf, pageable);
    }

    @Operation(summary = "Get a run by id", description = "Return a run")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public Run getRun(@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id)
        throws NoSuchEntityException, StoreException {
        return runManager.get(id);
    }

    @Operation(summary = "Update specific run", description = "Update and return the run")
    @PutMapping(
        path = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Run updateRun(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull Run dto
    ) throws NoSuchEntityException, IllegalArgumentException, SystemException, BindException, StoreException {
        return runManager.update(id, dto);
    }

    @Operation(summary = "Delete a run", description = "Delete a specific run, with optional cascade on logs")
    @DeleteMapping(path = "/{id}")
    public Run deleteRun(@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id)
        throws NoSuchEntityException, StoreException {
        Run run = runManager.get(id);

        //delete via manager
        return runManager.delete(run.getId(), true);
    }

    @Operation(summary = "Perform action on a specific run")
    @PostMapping(path = "/{id}/{action}")
    public Run performOnRun(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String action
    ) throws NoSuchEntityException, StoreException {
        Run run = runManager.get(id);

        // via manager
        return lifecycleManager.perform(run, action.toUpperCase());
    }

    @Operation(summary = "Get resource metrics", description = "Get metrics for a run")
    @GetMapping(path = "/{id}/resource_metrics", produces = "application/json; charset=UTF-8")
    public ResourceMetrics getResourceMetrics(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException, StoreException {
        if (resourceMetricsService == null) {
            throw new StoreException("metrics service not available");
        }

        Run run = runManager.get(id);
        return resourceMetricsService.getResourceMetricsByRun(run.getProject(), id);
    }
}
