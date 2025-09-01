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

package it.smartcommunitylabdhub.core.models.services;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.metrics.Metrics;
import it.smartcommunitylabdhub.commons.models.metrics.NumberOrNumberArray;
import it.smartcommunitylabdhub.commons.models.model.Model;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.commons.services.MetricsService;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.metrics.MetricsManager;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Transactional
@Slf4j
public class ModelMetricsServiceImpl implements MetricsService<Model> {

    @Autowired
    private EntityService<Model> entityService;

    @Autowired
    private MetricsManager metricsManager;

    @Override
    public Map<String, NumberOrNumberArray> getMetrics(@NotNull String entityId)
        throws StoreException, SystemException {
        log.debug("fetch all metrics for model {}", entityId);
        Model entity = entityService.get(entityId);

        //embedded metrics
        StatusFieldAccessor statusFieldAccessor = StatusFieldAccessor.with(entity.getStatus());
        Map<String, NumberOrNumberArray> embedded = statusFieldAccessor.getMetrics();

        //stored metrics
        Map<String, NumberOrNumberArray> stored = metricsManager.getMetrics(EntityName.MODEL.getValue(), entityId);

        //merge stored and embedded
        Map<String, NumberOrNumberArray> metrics = MapUtils.mergeMultipleMaps(stored, embedded);

        if (log.isTraceEnabled()) {
            log.trace("metrics: {}", metrics);
        }

        return stored;
    }

    @Override
    public NumberOrNumberArray getMetrics(@NotNull String entityId, @NotNull String name)
        throws StoreException, SystemException {
        log.debug("fetch metric {} for model {}", name, entityId);
        Model entity = entityService.get(entityId);

        //embedded metrics
        StatusFieldAccessor statusFieldAccessor = StatusFieldAccessor.with(entity.getStatus());
        Map<String, NumberOrNumberArray> embedded = statusFieldAccessor.getMetrics();
        if ((embedded != null) && embedded.containsKey(name)) {
            return embedded.get(name);
        } else {
            //stored metrics
            return metricsManager.getMetrics(EntityName.MODEL.getValue(), entityId, name);
        }
    }

    @Override
    public Metrics saveMetrics(@NotNull String entityId, @NotNull String name, NumberOrNumberArray data)
        throws StoreException, SystemException {
        log.debug("save metric {} for model {}", name, entityId);
        if (log.isTraceEnabled()) {
            log.trace("data: {}", data);
        }

        return metricsManager.saveMetrics(EntityName.MODEL.getValue(), entityId, name, data);
    }
}
