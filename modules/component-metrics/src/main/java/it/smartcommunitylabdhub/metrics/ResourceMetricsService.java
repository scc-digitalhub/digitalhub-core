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

package it.smartcommunitylabdhub.metrics;

import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/*
 * Service for accessing resource metrics
 * Metrics are aggregated data about resource usage, such as CPU, memory, and storage,
 * for a given project, user, or run. They can contain:
 * - a matrix of series, each with timestamp and value
 * - a summary of the metrics, such as average, min, max, and total
 */
public interface ResourceMetricsService {
    ResourceMetrics getResourceMetrics() throws SystemException;
    List<ResourceMetrics> listResourceMetrics() throws SystemException;

    ResourceMetrics getResourceMetricsByProject(@NotNull String project) throws SystemException;
    List<ResourceMetrics> listResourceMetricsByProject(@NotNull String project) throws SystemException;

    ResourceMetrics getResourceMetricsByUser(@NotNull String user) throws SystemException;
    List<ResourceMetrics> listResourceMetricsByUser(@NotNull String user) throws SystemException;

    ResourceMetrics getResourceMetricsByRun(@NotNull String project, @NotNull String runId) throws SystemException;
    List<ResourceMetrics> listResourceMetricsByRun(@NotNull String project, @NotNull String runId)
        throws SystemException;
}
