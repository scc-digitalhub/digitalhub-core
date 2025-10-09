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

package it.smartcommunitylabdhub.core.controllers.v1.base;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.services.RunManager;
import it.smartcommunitylabdhub.core.ApplicationKeys;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.runs.filters.RunEntityFilter;
import it.smartcommunitylabdhub.core.runs.lifecycle.KindAwareRunLifecycleManager;
import it.smartcommunitylabdhub.runtimes.lifecycle.RunEvent;
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
@ApiVersion("v1")
@RequestMapping("/runs")
//TODO evaluate permissions for project via lookup in dto
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Validated
@Slf4j
@Tag(name = "Run base API", description = "Endpoints related to runs management out of the Context")
public class RunController {

    @Autowired
    RunManager runManager;

    @Autowired
    KindAwareRunLifecycleManager lifecycleManager;

    @Operation(summary = "Create run and exec", description = "Create a run and exec")
    @PostMapping(
        value = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Run createRun(@RequestBody @Valid @NotNull Run dto)
        throws DuplicatedEntityException, NoSuchEntityException, SystemException, BindException {
        //create as new, will check for duplicated
        Run run = runManager.createRun(dto);

        //if !local then also build+run
        RunBaseSpec runBaseSpec = new RunBaseSpec();
        runBaseSpec.configure(run.getSpec());

        if (Boolean.FALSE.equals(runBaseSpec.getLocalExecution())) {
            run = lifecycleManager.perform(run, RunEvent.BUILD.name());
            run = lifecycleManager.perform(run, RunEvent.RUN.name());
        }

        return run;
    }

    @Operation(summary = "List runs", description = "Return a list of all runs")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<Run> getRuns(
        @ParameterObject @Valid @Nullable RunEntityFilter filter,
        @ParameterObject @PageableDefault(page = 0, size = ApplicationKeys.DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "created", direction = Direction.DESC) }
        ) Pageable pageable
    ) {
        SearchFilter<Run> sf = null;
        if (filter != null) {
            sf = filter.toSearchFilter();
        }

        return runManager.searchRuns(pageable, sf);
    }

    @Operation(summary = "Get a run by id", description = "Return a run")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public Run getRun(@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id)
        throws NoSuchEntityException {
        return runManager.getRun(id);
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
    ) throws NoSuchEntityException, IllegalArgumentException, SystemException, BindException {
        return runManager.updateRun(id, dto);
    }

    @Operation(summary = "Delete a run", description = "Delete a specific run, with optional cascade on logs")
    @DeleteMapping(path = "/{id}")
    public Run deleteRun(@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id) {
        Run run = runManager.getRun(id);

        //delete via manager
        return lifecycleManager.perform(run, RunEvent.DELETE.name());
    }

    @Operation(summary = "Perform action on a specific run")
    @PostMapping(path = "/{id}/{action}")
    public Run performOnRun(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String action
    ) throws NoSuchEntityException {
        Run run = runManager.getRun(id);

        // via manager
        return lifecycleManager.perform(run, action.toUpperCase());
    }
}
