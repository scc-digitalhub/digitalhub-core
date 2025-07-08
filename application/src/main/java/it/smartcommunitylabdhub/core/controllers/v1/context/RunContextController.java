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
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.log.Log;
import it.smartcommunitylabdhub.commons.models.metrics.NumberOrNumberArray;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.services.LogService;
import it.smartcommunitylabdhub.commons.services.MetricsService;
import it.smartcommunitylabdhub.commons.services.RelationshipsAwareEntityService;
import it.smartcommunitylabdhub.commons.services.RunManager;
import it.smartcommunitylabdhub.core.ApplicationKeys;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.runs.filters.RunEntityFilter;
import it.smartcommunitylabdhub.core.runs.lifecycle.RunLifecycleManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
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
@ApiVersion("v1")
@RequestMapping("/-/{project}/runs")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "Run context API", description = "Endpoints related to runs management for project")
public class RunContextController {

    @Autowired
    RunManager runManager;

    @Autowired
    RunLifecycleManager lifecycleManager;

    @Autowired
    LogService logService;

    @Autowired
    RelationshipsAwareEntityService<Run> relationshipsService;

    @Autowired
    MetricsService<Run> metricsService;

    @Operation(summary = "Create a run in a project context")
    @PostMapping(
        value = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Run createRun(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @Valid @NotNull @RequestBody Run dto
    ) throws DuplicatedEntityException, NoSuchEntityException, SystemException, BindException {
        //enforce project match
        dto.setProject(project);

        //create as new, will check for duplicated
        Run run = runManager.createRun(dto);

        //if !local then also build+run
        RunBaseSpec runBaseSpec = new RunBaseSpec();
        runBaseSpec.configure(run.getSpec());

        if (Boolean.FALSE.equals(runBaseSpec.getLocalExecution())) {
            run = lifecycleManager.build(run);
            run = lifecycleManager.run(run);
        }

        return run;
    }

    @Operation(summary = "Retrieve all runs for the project, with optional filter")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<Run> searchRuns(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @ParameterObject @Valid @Nullable RunEntityFilter filter,
        @ParameterObject @PageableDefault(page = 0, size = ApplicationKeys.DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "created", direction = Direction.DESC) }
        ) Pageable pageable
    ) {
        SearchFilter<Run> sf = null;
        if (filter != null) {
            sf = filter.toSearchFilter();
        }

        return runManager.searchRunsByProject(project, pageable, sf);
    }

    @Operation(summary = "Retrieve a specific run given the run id")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public Run getRunById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Run run = runManager.getRun(id);

        //check for project match
        if (!run.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return run;
    }

    @Operation(summary = "Update if exist a run in a project context")
    @PutMapping(
        value = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Run updateRunById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull Run runDTO
    ) throws NoSuchEntityException, IllegalArgumentException, SystemException, BindException {
        Run run = runManager.getRun(id);

        //check for project match
        if (!run.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return runManager.updateRun(id, runDTO);
    }

    @Operation(summary = "Delete a specific run, with optional cascade")
    @DeleteMapping(path = "/{id}")
    public Run deleteRun(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Run run = runManager.getRun(id);

        //check for project  match
        if (!run.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        //delete via manager
        return lifecycleManager.delete(run);
    }

    /*
     * Actions
     */
    @Operation(summary = "Build a specific run")
    @PostMapping(path = "/{id}/build")
    public Run buildRunById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Run run = runManager.getRun(id);

        //check for project  match
        if (!run.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        // via manager
        return lifecycleManager.build(run);
    }

    @Operation(summary = "Execute a specific run")
    @PostMapping(path = "/{id}/run")
    public Run runRunById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Run run = runManager.getRun(id);

        //check for project  match
        if (!run.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        // via manager
        return lifecycleManager.run(run);
    }

    @Operation(summary = "Stop a specific run execution")
    @PostMapping(path = "/{id}/stop")
    public Run stopRunById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Run run = runManager.getRun(id);

        //check for project  match
        if (!run.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        // via manager
        return lifecycleManager.stop(run);
    }

    @Operation(summary = "Resume a specific run execution")
    @PostMapping(path = "/{id}/resume")
    public Run resumeRunById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Run run = runManager.getRun(id);

        //check for project  match
        if (!run.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        // via manager
        return lifecycleManager.resume(run);
    }

    @Operation(summary = "Delete a specific run execution")
    @PostMapping(path = "/{id}/delete")
    public Run deleteRunById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Run run = runManager.getRun(id);

        //check for project  match
        if (!run.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        // via manager
        return lifecycleManager.delete(run);
    }

    /*
     * Logs
     */

    @Operation(summary = "List logs for a given run", description = "Return a list of logs defined for a specific run")
    @GetMapping(path = "/{id}/logs", produces = "application/json; charset=UTF-8")
    public List<Log> getLogsByRunId(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Run run = runManager.getRun(id);

        //check for project
        if (!run.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return logService.getLogsByRunId(id);
    }

    @Operation(summary = "Get relationships info for a given entity, if available")
    @GetMapping(path = "/{id}/relationships", produces = "application/json; charset=UTF-8")
    public List<RelationshipDetail> getRelationshipsById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Run entity = runManager.getRun(id);

        //check for project and name match
        if ((entity != null) && !entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return relationshipsService.getRelationships(id);
    }

    @Operation(summary = "Get metrics info for a given entity, if available")
    @GetMapping(path = "/{id}/metrics", produces = "application/json; charset=UTF-8")
    public Map<String, NumberOrNumberArray> getMetrics(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws StoreException, SystemException {
        Run entity = runManager.getRun(id);

        //check for project and name match
        if ((entity != null) && !entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return metricsService.getMetrics(id);
    }

    @Operation(summary = "Get metrics info for a given entity and metric, if available")
    @GetMapping(path = "/{id}/metrics/{name}", produces = "application/json; charset=UTF-8")
    public NumberOrNumberArray getMetricsByName(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @PathVariable String name
    ) throws StoreException, SystemException {
        Run entity = runManager.getRun(id);

        //check for project and name match
        if ((entity != null) && !entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return metricsService.getMetrics(id, name);
    }

    @Operation(summary = "Store metrics info for a given entity")
    @PutMapping(path = "/{id}/metrics/{name}", produces = "application/json; charset=UTF-8")
    public void storeMetrics(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @PathVariable String name,
        @RequestBody NumberOrNumberArray data
    ) throws StoreException, SystemException {
        Run entity = runManager.getRun(id);

        //check for project and name match
        if ((entity != null) && !entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        metricsService.saveMetrics(id, name, data);
    }
}
