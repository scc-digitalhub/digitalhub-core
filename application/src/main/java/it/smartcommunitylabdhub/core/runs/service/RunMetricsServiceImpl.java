/**
 * Copyright 2025 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.core.runs.service;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.metrics.Metrics;
import it.smartcommunitylabdhub.commons.models.metrics.NumberOrNumberArray;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.commons.services.MetricsService;
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
public class RunMetricsServiceImpl implements MetricsService<Run> {

    @Autowired
    private EntityService<Run> entityService;

    @Autowired
    private MetricsManager metricsManager;

    @Override
    public Map<String, NumberOrNumberArray> getMetrics(@NotNull String entityId)
        throws StoreException, SystemException {
        log.debug("fetch all metrics {} for run {}", entityId);

        Run entity = entityService.get(entityId);
        StatusFieldAccessor statusFieldAccessor = StatusFieldAccessor.with(entity.getStatus());
        Map<String, NumberOrNumberArray> metrics = statusFieldAccessor.getMetrics();
        if (metrics != null) {
            Map<String, NumberOrNumberArray> entityMetrics = metricsManager.getMetrics(
                EntityName.RUN.getValue(),
                entityId
            );
            for (Map.Entry<String, NumberOrNumberArray> entry : entityMetrics.entrySet()) {
                if (metrics.containsKey(entry.getKey())) continue;
                metrics.put(entry.getKey(), entry.getValue());
            }
            return metrics;
        }
        return metricsManager.getMetrics(EntityName.RUN.getValue(), entityId);
    }

    @Override
    public NumberOrNumberArray getMetrics(@NotNull String entityId, @NotNull String name)
        throws StoreException, SystemException {
        log.debug("fetch metric {} for run {}", name, entityId);
        Run entity = entityService.get(entityId);

        //embedded metrics
        StatusFieldAccessor statusFieldAccessor = StatusFieldAccessor.with(entity.getStatus());
        Map<String, NumberOrNumberArray> metrics = statusFieldAccessor.getMetrics();
        if ((metrics != null) && metrics.containsKey(name)) return metrics.get(name);
        return metricsManager.getMetrics(EntityName.RUN.getValue(), entityId, name);
    }

    @Override
    public Metrics saveMetrics(@NotNull String entityId, @NotNull String name, NumberOrNumberArray data)
        throws StoreException, SystemException {
        log.debug("save metric {} for run {}", name, entityId);
        if (log.isTraceEnabled()) {
            log.trace("data: {}", data);
        }

        return metricsManager.saveMetrics(EntityName.RUN.getValue(), entityId, name, data);
    }
}
