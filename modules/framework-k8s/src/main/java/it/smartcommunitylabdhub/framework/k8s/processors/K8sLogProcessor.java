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

import it.smartcommunitylabdhub.commons.annotations.common.RunProcessorType;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.infrastructure.RunProcessor;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.models.log.Log;
import it.smartcommunitylabdhub.commons.models.log.LogSpec;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import it.smartcommunitylabdhub.commons.services.LogService;
import it.smartcommunitylabdhub.framework.k8s.model.K8sLogStatus;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLog;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreMetric;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;

@RunProcessorType(stages = { "onRunning", "onCompleted", "onError", "onStopped", "onDeleted" }, id = K8sLogProcessor.ID)
@Component(K8sLogProcessor.ID)
public class K8sLogProcessor implements RunProcessor<RunBaseStatus> {

    public static final String ID = "k8sLogProcessor";

    //TODO make configurable
    public static final int MAX_METRICS = 300;

    private final LogService logService;

    public K8sLogProcessor(LogService logService) {
        Assert.notNull(logService, "log service is required to persist logs");
        this.logService = logService;
    }

    @Override
    public RunBaseStatus process(Run run, RunRunnable runRunnable, RunBaseStatus status) {
        if (runRunnable instanceof K8sRunnable) {
            //extract logs
            List<CoreLog> logs = ((K8sRunnable) runRunnable).getLogs();
            List<CoreMetric> metrics = ((K8sRunnable) runRunnable).getMetrics();

            if (logs != null) {
                writeLogs(run, logs, metrics);
            }
        }

        return null;
    }

    private void writeLogs(Run run, List<CoreLog> logs, List<CoreMetric> metrics) {
        String runId = run.getId();
        Instant now = Instant.now();

        //logs are grouped by pod+container, search by run and create/append
        Map<String, Log> entries = logService
            .getLogsByRunId(runId)
            .stream()
            .map(e -> {
                K8sLogStatus status = new K8sLogStatus();
                status.configure(e.getStatus());

                String pod = status.getPod() != null ? status.getPod() : "";
                String container = status.getContainer() != null ? status.getContainer() : "";
                String namespace = status.getNamespace() != null ? status.getNamespace() : "";
                String key = namespace + pod + container;

                if (StringUtils.hasText(runId)) {
                    return Map.entry(key, e);
                } else {
                    return null;
                }
            })
            .filter(e -> e != null)
            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));

        //reformat metrics grouped per container
        //TODO refactor
        Map<String, HashMap<String, Serializable>> mmetrics = new HashMap<>();
        if (metrics != null) {
            metrics.forEach(m -> {
                if (m.metrics() != null) {
                    m
                        .metrics()
                        .forEach(cm -> {
                            String key = m.namespace() + m.pod() + cm.getName();
                            if (cm.getUsage() != null) {
                                HashMap<String, String> usage = cm
                                    .getUsage()
                                    .entrySet()
                                    .stream()
                                    .collect(
                                        Collectors.toMap(
                                            e -> e.getKey(),
                                            e -> e.getValue().toSuffixedString(),
                                            (prev, next) -> next,
                                            HashMap::new
                                        )
                                    );

                                HashMap<String, Serializable> mm = new HashMap<>();
                                mm.put("timestamp", m.timestamp());
                                mm.put("window", m.window());
                                mm.put("usage", usage);
                                mmetrics.put(key, mm);
                            }
                        });
                }
            });
        }

        logs.forEach(l -> {
            try {
                String key = l.namespace() + l.pod() + l.container();

                if (entries.get(key) != null) {
                    //update
                    Log log = entries.get(key);
                    log.setContent(l.value());

                    //check if metric is available
                    if (mmetrics.containsKey(key)) {
                        HashMap<String, Serializable> metric = mmetrics.get(key);

                        //append to status
                        K8sLogStatus logStatus = new K8sLogStatus();
                        logStatus.configure(log.getStatus());

                        List<Serializable> list = logStatus.getMetrics() != null
                            ? new ArrayList<>(logStatus.getMetrics())
                            : new ArrayList<>();

                        list.addLast(metric);
                        logStatus.setMetrics(list);

                        //check if we need to slice
                        //TODO cleanup
                        if (list.size() > MAX_METRICS) {
                            Collections.reverse(list);
                            List<Serializable> slice = new ArrayList<>(list.subList(0, MAX_METRICS));
                            Collections.reverse(slice);
                            logStatus.setMetrics(slice);
                        }

                        log.setStatus(logStatus.toMap());
                    }

                    logService.updateLog(log.getId(), log);
                } else {
                    //add as new
                    LogSpec logSpec = new LogSpec();
                    logSpec.setRun(runId);
                    logSpec.setTimestamp(now.toEpochMilli());

                    K8sLogStatus logStatus = new K8sLogStatus();
                    logStatus.setPod(l.pod());
                    logStatus.setContainer(l.container());
                    logStatus.setNamespace(l.namespace());

                    //check if metric is available
                    if (mmetrics.containsKey(key)) {
                        HashMap<String, Serializable> metric = mmetrics.get(key);

                        //append to status
                        List<Serializable> list = logStatus.getMetrics() != null
                            ? new ArrayList<>(logStatus.getMetrics())
                            : new ArrayList<>();
                        list.addLast(metric);
                        logStatus.setMetrics(list);

                        //check if we need to slice
                        //TODO cleanup
                        if (list.size() > MAX_METRICS) {
                            Collections.reverse(list);
                            List<Serializable> slice = new ArrayList<>(list.subList(0, MAX_METRICS));
                            Collections.reverse(slice);
                            logStatus.setMetrics(slice);
                        }
                    }

                    Log log = Log
                        .builder()
                        .project(run.getProject())
                        .spec(logSpec.toMap())
                        .status(logStatus.toMap())
                        .content(l.value())
                        .build();

                    logService.createLog(log);
                }
            } catch (
                NoSuchEntityException
                | IllegalArgumentException
                | SystemException
                | BindException
                | DuplicatedEntityException e
            ) {
                //invalid, skip
                //TODO handle
            }
        });
    }
}
