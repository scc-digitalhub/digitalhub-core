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
import it.smartcommunitylabdhub.framework.k8s.model.K8sLogStatus;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLog;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.logs.Log;
import it.smartcommunitylabdhub.logs.LogStore;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runs.specs.RunBaseStatus;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;

@ProcessorType(
    stages = { "onRunning", "onCompleted", "onError", "onStopped", "onDeleted" },
    type = Run.class,
    spec = Status.class
)
@Component
@ConditionalOnBean(LogStore.class)
public class K8sLogProcessor implements Processor<Run, RunBaseStatus> {

    //TODO make configurable
    public static final int MAX_METRICS = 300;

    private final LogStore logService;

    public K8sLogProcessor(LogStore logService) {
        Assert.notNull(logService, "log service is required to persist logs");
        this.logService = logService;
    }

    @Override
    public RunBaseStatus process(String stage, Run run, Serializable input) throws CoreRuntimeException {
        if (input instanceof K8sRunnable runnable) {
            //extract logs
            List<CoreLog> logs = runnable.getLogs();

            if (logs != null) {
                writeLogs(run, logs);
            }
        }

        return null;
    }

    private void writeLogs(Run run, List<CoreLog> logs) {
        String runId = run.getId();
        Instant now = Instant.now();

        //logs are grouped by pod+container, search by run and create/append
        Map<String, Log> entries = logService
            .getLogsByRunId(runId)
            .stream()
            .map(e -> {
                K8sLogStatus status = new K8sLogStatus();
                status.configure(e.getExtensions());

                String pod = status.getPod() != null ? status.getPod() : "";
                String container = status.getContainer() != null ? status.getContainer() : "";
                String namespace = status.getNamespace() != null ? status.getNamespace() : "";
                String containerId = status.getContainerId() != null ? status.getContainerId() : "";
                String key = namespace + pod + container + containerId;

                if (StringUtils.hasText(key)) {
                    return Map.entry(key, e);
                } else {
                    return null;
                }
            })
            .filter(e -> e != null)
            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));

        logs.forEach(l -> {
            try {
                String baseKey = l.namespace() + l.pod() + l.container();
                String key = baseKey + (l.containerId() != null ? l.containerId() : "");

                if (entries.get(key) != null) {
                    //update
                    Log log = entries.get(key);
                    log.setContent(l.value());

                    logService.updateLog(log.getId(), log);
                } else {
                    //add as new

                    K8sLogStatus logStatus = new K8sLogStatus();
                    logStatus.setPod(l.pod());
                    logStatus.setContainer(l.container());
                    logStatus.setNamespace(l.namespace());
                    logStatus.setContainerId(l.containerId());

                    Log log = Log.builder()
                        .project(run.getProject())
                        .run(run.getId())
                        .extensions(logStatus.toMap())
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
