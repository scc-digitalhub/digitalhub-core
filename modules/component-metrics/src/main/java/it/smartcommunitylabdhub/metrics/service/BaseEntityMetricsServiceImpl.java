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

package it.smartcommunitylabdhub.metrics.service;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.metrics.Metrics;
import it.smartcommunitylabdhub.commons.models.metrics.NumberOrNumberArray;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.metrics.MetricsManager;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Transactional
@Slf4j
public class BaseEntityMetricsServiceImpl<D extends BaseDTO> implements MetricsService<D> {

    private EntityService<D> entityService;
    private MetricsManager metricsManager;

    protected final Class<D> type;

    @Autowired
    public void setEntityService(EntityService<D> entityService) {
        this.entityService = entityService;
    }

    @Autowired
    public void setMetricsManager(MetricsManager metricsManager) {
        this.metricsManager = metricsManager;
    }

    @SuppressWarnings("unchecked")
    protected BaseEntityMetricsServiceImpl() {
        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.type = (Class<D>) t;
    }

    public BaseEntityMetricsServiceImpl(Class<D> type) {
        this.type = type;
    }

    @Override
    public Class<D> getType() {
        return type;
    }

    @Override
    public Map<String, NumberOrNumberArray> getMetrics(@NotNull String entityId)
        throws StoreException, SystemException {
        log.debug("fetch all metrics for {}", entityId);
        D entity = entityService.get(entityId);

        Map<String, NumberOrNumberArray> embedded = null;
        if (entity instanceof StatusDTO statusEntity) {
            //embedded metrics
            StatusFieldAccessor statusFieldAccessor = StatusFieldAccessor.with(statusEntity.getStatus());
            embedded = statusFieldAccessor.getMetrics();
        }

        //stored metrics
        Map<String, NumberOrNumberArray> stored = metricsManager.getMetrics(EntityUtils.getEntityName(type), entityId);

        //merge stored and embedded
        Map<String, NumberOrNumberArray> metrics = MapUtils.mergeMultipleMaps(stored, embedded);

        if (log.isTraceEnabled()) {
            log.trace("metrics: {}", metrics);
        }

        return metrics;
    }

    @Override
    public List<Metrics> saveMetrics(@NotNull String entityId, @NotNull Map<String, NumberOrNumberArray> data)
        throws StoreException, SystemException {
        log.debug("save metrics for {}", entityId);
        if (log.isTraceEnabled()) {
            log.trace("data: {}", data);
        }

        if (data == null || data.isEmpty()) {
            return List.of();
        }
        return data
            .entrySet()
            .stream()
            .map(entry -> {
                try {
                    return saveMetrics(entityId, entry.getKey(), entry.getValue());
                } catch (SystemException | StoreException e) {
                    log.error("Error saving metrics {}: {}", entry.getKey(), e);
                    return null;
                }
            })
            .filter(e -> e != null)
            .toList();
    }

    @Override
    public void deleteMetrics(@NotNull String entityId) throws StoreException, SystemException {
        log.debug("delete metrics for {}", entityId);

        //stored metrics only
        metricsManager
            .getMetrics(EntityUtils.getEntityName(type), entityId)
            .keySet()
            .forEach(name -> {
                try {
                    deleteMetrics(entityId, name);
                } catch (SystemException | StoreException e) {
                    log.error("Error deleting metric {}: {}", name, e);
                }
            });
    }

    @Override
    public NumberOrNumberArray getMetrics(@NotNull String entityId, @NotNull String name)
        throws StoreException, SystemException {
        log.debug("fetch metric {} for {}", name, entityId);
        D entity = entityService.get(entityId);

        Map<String, NumberOrNumberArray> embedded = null;
        if (entity instanceof StatusDTO statusEntity) {
            //embedded metrics
            StatusFieldAccessor statusFieldAccessor = StatusFieldAccessor.with(statusEntity.getStatus());
            embedded = statusFieldAccessor.getMetrics();
        }

        //stored metrics
        NumberOrNumberArray stored = metricsManager.getMetrics(EntityUtils.getEntityName(type), entityId, name);

        //merge stored and embedded
        Map<String, NumberOrNumberArray> metrics = MapUtils.mergeMultipleMaps(Map.of(name, stored), embedded);

        if (log.isTraceEnabled()) {
            log.trace("metrics: {}", metrics);
        }

        return metrics.get(name);
    }

    @Override
    public Metrics saveMetrics(@NotNull String entityId, @NotNull String name, NumberOrNumberArray data)
        throws StoreException, SystemException {
        log.debug("save metric {} for {}", name, entityId);
        if (log.isTraceEnabled()) {
            log.trace("data: {}", data);
        }

        return metricsManager.saveMetrics(EntityUtils.getEntityName(type), entityId, name, data);
    }

    @Override
    public Metrics appendMetrics(@NotNull String entityId, @NotNull String name, NumberOrNumberArray data)
        throws StoreException, SystemException {
        log.debug("append metric {} for {}", name, entityId);
        if (log.isTraceEnabled()) {
            log.trace("data: {}", data);
        }

        //fetch stored metrics
        NumberOrNumberArray stored = metricsManager.getMetrics(EntityUtils.getEntityName(type), entityId, name);

        //append will always produce an array
        List<Double> values = new LinkedList<>();
        if (stored != null) {
            if (stored.getValues() != null && !stored.getValues().isEmpty()) {
                values.addAll(stored.getValues());
            } else if (stored.getValue() != null) {
                values.add(stored.getValue());
            }
        }
        if (data != null) {
            if (data.getValues() != null && !data.getValues().isEmpty()) {
                values.addAll(data.getValues());
            } else if (data.getValue() != null) {
                values.add(data.getValue());
            }
        }

        NumberOrNumberArray updated = new NumberOrNumberArray(values);
        return metricsManager.saveMetrics(EntityUtils.getEntityName(type), entityId, name, updated);
    }

    @Override
    public void deleteMetrics(@NotNull String entityId, @NotNull String name) throws StoreException, SystemException {
        log.debug("delete metric {} for {}", name, entityId);
        //delete ONLY stored metrics
        metricsManager.deleteMetrics(EntityUtils.getEntityName(type), entityId, name);
    }
}
