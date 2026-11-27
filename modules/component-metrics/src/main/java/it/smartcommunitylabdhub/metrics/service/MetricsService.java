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

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.metrics.Metrics;
import it.smartcommunitylabdhub.commons.models.metrics.NumberOrNumberArray;
import it.smartcommunitylabdhub.commons.services.EntityAwareService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public interface MetricsService<T extends BaseDTO> extends EntityAwareService<T> {
    //whole metrics as object for an entity
    public Map<String, NumberOrNumberArray> getMetrics(@NotNull String entityId) throws StoreException, SystemException;

    public List<Metrics> saveMetrics(@NotNull String entityId, @NotNull Map<String, NumberOrNumberArray> data)
        throws StoreException, SystemException;

    public void deleteMetrics(@NotNull String entityId) throws StoreException, SystemException;

    //name is the identifier for the specific metric within the entity
    public NumberOrNumberArray getMetrics(@NotNull String entityId, @NotNull String name)
        throws StoreException, SystemException;

    public Metrics saveMetrics(@NotNull String entityId, @NotNull String name, NumberOrNumberArray data)
        throws StoreException, SystemException;

    public Metrics appendMetrics(@NotNull String entityId, @NotNull String name, NumberOrNumberArray data)
        throws StoreException, SystemException;

    public void deleteMetrics(@NotNull String entityId, @NotNull String name) throws StoreException, SystemException;
}
