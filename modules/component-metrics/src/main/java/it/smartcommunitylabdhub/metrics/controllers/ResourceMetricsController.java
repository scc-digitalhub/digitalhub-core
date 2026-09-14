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

package it.smartcommunitylabdhub.metrics.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.metrics.ResourceMetrics;
import it.smartcommunitylabdhub.metrics.ResourceMetricsService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAuthority('ROLE_USER')")
@Validated
@Slf4j
@Tag(name = "Resource Metrics API", description = "Endpoints related to resource metrics")
public class ResourceMetricsController {

    @Autowired(required = false)
    private ResourceMetricsService resourceMetricsService;

    @Autowired
    private ApplicationProperties applicationProperties;

    @Operation(
        summary = "Get instance metrics",
        description = "Get metrics for the whole instance, including all projects and resources"
    )
    @GetMapping(path = "/resource_metrics", produces = "application/json; charset=UTF-8")
    public ResourceMetrics getMetrics(Authentication auth) throws NoSuchEntityException, StoreException {
        if (resourceMetricsService == null) {
            throw new StoreException("metrics service not available");
        }
        return resourceMetricsService.getResourceMetrics();
    }

    @Operation(
        summary = "Get project metrics",
        description = "Get metrics for a specific project, including all resources"
    )
    @PreAuthorize(
        "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
    )
    @GetMapping(
        path = { "/resource_metrics/projects/{project}", "/-/{project}/resource_metrics" },
        produces = "application/json; charset=UTF-8"
    )
    public List<ResourceMetrics> getMetricsByProject(Authentication auth, @PathVariable String project)
        throws NoSuchEntityException, StoreException {
        if (resourceMetricsService == null) {
            throw new StoreException("metrics service not available");
        }
        return resourceMetricsService.listResourceMetricsByProject(project);
    }

    @Operation(summary = "Get run metrics", description = "Get metrics for a specific run, including all resources")
    @PreAuthorize(
        "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
    )
    @GetMapping(path = "/-/{project}/resource_metrics/runs/{runId}", produces = "application/json; charset=UTF-8")
    public List<ResourceMetrics> getMetricsByRun(
        Authentication auth,
        @PathVariable String project,
        @PathVariable String runId
    ) throws NoSuchEntityException, StoreException {
        if (resourceMetricsService == null) {
            throw new StoreException("metrics service not available");
        }
        return resourceMetricsService.listResourceMetricsByRun(project, runId);
    }
}
