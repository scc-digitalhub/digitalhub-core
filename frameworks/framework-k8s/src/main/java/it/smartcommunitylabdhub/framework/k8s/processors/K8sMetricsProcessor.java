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

package it.smartcommunitylabdhub.framework.k8s.processors;

import it.smartcommunitylabdhub.commons.annotations.common.ProcessorType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.models.status.Status;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreMetric;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.metrics.ResourceMetrics;
import it.smartcommunitylabdhub.metrics.ResourceMetricsStore;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runs.specs.RunBaseStatus;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.validation.BindException;

@ProcessorType(
    stages = { "onRunning", "onCompleted", "onError", "onStopped", "onDeleted" },
    type = Run.class,
    spec = Status.class
)
@Component
@ConditionalOnBean(ResourceMetricsStore.class)
@Slf4j
public class K8sMetricsProcessor implements Processor<Run, RunBaseStatus> {

    //TODO make configurable
    public static final int MAX_METRICS = 300;

    private final ResourceMetricsStore store;

    public K8sMetricsProcessor(ResourceMetricsStore store) {
        Assert.notNull(store, "metrics store is required to persist metrics");
        this.store = store;
    }

    @Override
    public RunBaseStatus process(String stage, Run run, Serializable input) throws CoreRuntimeException {
        if (input instanceof K8sRunnable runnable) {
            //extract logs
            List<CoreMetric> metrics = runnable.getMetrics();

            if (metrics != null) {
                writeMetrics(run, metrics);
            }
        }

        return null;
    }

    private void writeMetrics(Run run, @NotNull List<CoreMetric> metrics) {
        String runId = run.getId();
        Instant now = Instant.now();

        //metrics are stored per container in the store
        // id is generated as m_r-<runId>-<podName>-<containerName>
        metrics.forEach(m -> {
            if (m.metrics() != null) {
                Instant timestamp = m.timestamp() != null ? Instant.parse(m.timestamp()) : now;

                m
                    .metrics()
                    .forEach(cm -> {
                        String id = "m_r-" + runId + "-" + m.pod() + "-" + cm.getName();
                        if (cm.getUsage() != null) {
                            Map<String, List<ResourceMetrics.Metric>> usage = cm
                                .getUsage()
                                .entrySet()
                                .stream()
                                .collect(
                                    Collectors.toMap(Map.Entry::getKey, e -> {
                                        BigDecimal value = e.getValue().getNumber();
                                        return List.of(
                                            new ResourceMetrics.Metric(timestamp.toEpochMilli(), value.doubleValue())
                                        );
                                    })
                                );

                            ResourceMetrics rm = store.findResourceMetrics(id);
                            if (rm == null) {
                                //create as new
                                rm = ResourceMetrics.builder()
                                    .id(id)
                                    .project(run.getProject())
                                    .run(runId)
                                    .metrics(usage)
                                    .build();
                                try {
                                    rm = store.createResourceMetrics(rm);
                                } catch (
                                    IllegalArgumentException
                                    | SystemException
                                    | DuplicatedEntityException
                                    | BindException e1
                                ) {
                                    log.error("Error creating metrics for run {}: {}", runId, e1.getMessage());
                                }
                            } else {
                                //append new values and update
                                Map<String, List<ResourceMetrics.Metric>> existing = rm.getMetrics();
                                usage.forEach((k, v) -> {
                                    List<ResourceMetrics.Metric> list = existing.getOrDefault(k, new ArrayList<>());
                                    list.addAll(v);
                                    existing.put(k, list);
                                });

                                //make sure usage list are sorted by timestamp
                                existing.forEach((k, v) -> {
                                    v.sort((m1, m2) -> Long.compare(m1.timestamp(), m2.timestamp()));
                                });

                                rm.setMetrics(existing);
                                try {
                                    rm = store.updateResourceMetrics(rm.getId(), rm);
                                } catch (
                                    NoSuchEntityException
                                    | IllegalArgumentException
                                    | SystemException
                                    | BindException e1
                                ) {
                                    log.error("Error updating metrics for run {}: {}", runId, e1.getMessage());
                                }
                            }
                        }
                    });
            }
        });
    }
}
