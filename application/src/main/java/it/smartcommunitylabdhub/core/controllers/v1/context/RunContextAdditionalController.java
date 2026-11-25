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
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.logs.Log;
import it.smartcommunitylabdhub.logs.LogService;
import it.smartcommunitylabdhub.metrics.ResourceMetrics;
import it.smartcommunitylabdhub.metrics.ResourceMetricsService;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runs.RunManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/-/{project}/runs")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "Run context API", description = "Endpoints related to runs management for project")
public class RunContextAdditionalController {

    @Autowired
    private RunManager runManager;

    @Autowired(required = false)
    private LogService logService;

    @Autowired(required = false)
    private ResourceMetricsService resourceMetricsService;

    /*
     * Logs
     */

    @Operation(summary = "List logs for a given run", description = "Return a list of logs defined for a specific run")
    @GetMapping(path = "/{id}/logs", produces = "application/json; charset=UTF-8")
    public List<Log> getLogsByRunId(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException, StoreException {
        if (logService == null) {
            throw new SystemException("log service not available");
        }

        Run run = runManager.get(id);

        //check for project
        if (!run.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return logService.getLogsByRunId(id);
    }

    @Operation(summary = "Get resource metrics", description = "Get metrics for a run")
    @GetMapping(path = "/{id}/resource_metrics", produces = "application/json; charset=UTF-8")
    public ResourceMetrics getResourceMetrics(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException, StoreException {
        if (resourceMetricsService == null) {
            throw new StoreException("metrics service not available");
        }
        Run run = runManager.get(id);

        //check for project and name match
        if (!run.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return resourceMetricsService.getResourceMetricsByRun(project, id);
    }
}
