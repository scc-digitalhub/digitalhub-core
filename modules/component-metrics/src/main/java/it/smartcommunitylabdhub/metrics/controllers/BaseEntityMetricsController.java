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
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.metrics.NumberOrNumberArray;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.metrics.service.MetricsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Slf4j
public class BaseEntityMetricsController<D extends BaseDTO> {

    protected EntityService<D> entityService;
    protected MetricsService<D> metricsService;

    @Autowired
    public void setEntityService(EntityService<D> entityService) {
        this.entityService = entityService;
    }

    @Autowired
    public void setMetricsService(MetricsService<D> metricsService) {
        this.metricsService = metricsService;
    }

    @Operation(summary = "Get metrics info for a given entity, if available")
    @GetMapping(path = "/{id}/metrics", produces = "application/json; charset=UTF-8")
    public Map<String, NumberOrNumberArray> getMetrics(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws StoreException, SystemException {
        D dto = entityService.get(id);

        //check for project and name match
        if (!dto.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return metricsService.getMetrics(id);
    }

    @Operation(summary = "Store metrics info for a given entity")
    @PutMapping(path = "/{id}/metrics", produces = "application/json; charset=UTF-8")
    public void storeMetrics(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody Map<String, NumberOrNumberArray> data
    ) throws StoreException, SystemException {
        D dto = entityService.get(id);

        //check for project and name match
        if (!dto.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        metricsService.saveMetrics(id, data);
    }

    @Operation(summary = "Delete metrics info for a given entity")
    @DeleteMapping(path = "/{id}/metrics", produces = "application/json; charset=UTF-8")
    public void deleteMetricsByName(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws StoreException, SystemException {
        D dto = entityService.get(id);

        //check for project and name match
        if (!dto.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        metricsService.deleteMetrics(id);
    }

    /*
     * By name
     */

    @Operation(summary = "Get metrics info for a given entity and metric, if available")
    @GetMapping(path = "/{id}/metrics/{name}", produces = "application/json; charset=UTF-8")
    public NumberOrNumberArray getMetricsByName(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @PathVariable @Valid @NotNull String name
    ) throws StoreException, SystemException {
        D dto = entityService.get(id);

        //check for project and name match
        if (!dto.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return metricsService.getMetrics(id, name);
    }

    @Operation(summary = "Store metrics info for a given entity")
    @PutMapping(path = "/{id}/metrics/{name}", produces = "application/json; charset=UTF-8")
    public void storeMetricsByName(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @PathVariable @Valid @NotNull String name,
        @RequestBody NumberOrNumberArray data
    ) throws StoreException, SystemException {
        D dto = entityService.get(id);

        //check for project and name match
        if (!dto.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        metricsService.saveMetrics(id, name, data);
    }

    @Operation(summary = "Append metrics info for a given entity")
    @PostMapping(path = "/{id}/metrics/{name}", produces = "application/json; charset=UTF-8")
    public void appendMetricsByName(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @PathVariable @Valid @NotNull String name,
        @RequestBody NumberOrNumberArray data
    ) throws StoreException, SystemException {
        D dto = entityService.get(id);

        //check for project and name match
        if (!dto.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        metricsService.appendMetrics(id, name, data);
    }

    @Operation(summary = "Delete metrics info for a given entity")
    @DeleteMapping(path = "/{id}/metrics/{name}", produces = "application/json; charset=UTF-8")
    public void deleteMetricsByName(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @PathVariable @Valid @NotNull String name
    ) throws StoreException, SystemException {
        D dto = entityService.get(id);

        //check for project and name match
        if (!dto.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        metricsService.deleteMetrics(id, name);
    }
}
