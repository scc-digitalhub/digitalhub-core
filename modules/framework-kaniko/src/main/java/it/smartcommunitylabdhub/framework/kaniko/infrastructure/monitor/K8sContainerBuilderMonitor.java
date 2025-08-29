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

package it.smartcommunitylabdhub.framework.kaniko.infrastructure.monitor;

import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1Pod;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.MonitorComponent;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sBaseFramework;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.monitor.K8sBaseMonitor;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnableState;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sContainerBuilderRunnable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@ConditionalOnKubernetes
@Component
@MonitorComponent(framework = K8sContainerBuilderRunnable.FRAMEWORK)
public class K8sContainerBuilderMonitor extends K8sBaseMonitor<K8sContainerBuilderRunnable> {

    @Autowired(required = false)
    private K8sBaseFramework<K8sContainerBuilderRunnable, V1Job> framework;

    public K8sContainerBuilderMonitor(RunnableStore<K8sContainerBuilderRunnable> runnableStore) {
        super(runnableStore);
    }

    @Override
    public K8sContainerBuilderRunnable refresh(K8sContainerBuilderRunnable runnable) {
        if (framework == null) {
            log.warn("No builder framework available");
            return runnable;
        }

        try {
            V1Job job = framework.get(framework.build(runnable));

            if (job == null || job.getStatus() == null) {
                // something is missing, no recovery
                log.error("Missing or invalid job for {}", runnable.getId());
                runnable.setState(K8sRunnableState.ERROR.name());
                runnable.setError("Job missing or invalid");
            }

            log.info("Job status: {}", job.getStatus().toString());

            //target for succeded/failed is 1
            if (job.getStatus().getSucceeded() != null && job.getStatus().getSucceeded().intValue() > 0) {
                // Job has succeeded
                runnable.setState(K8sRunnableState.COMPLETED.name());
            } else if (job.getStatus().getFailed() != null && job.getStatus().getFailed().intValue() > 0) {
                // Job has failed delete job and pod
                runnable.setState(K8sRunnableState.ERROR.name());
                runnable.setError("Job failed: " + job.getStatus().getFailed());
            }

            //try to fetch pods
            List<V1Pod> pods = null;
            try {
                pods = framework.pods(job);
            } catch (K8sFrameworkException e1) {
                log.error("error collecting pods for job {}: {}", runnable.getId(), e1.getMessage());
            }

            //update results
            try {
                runnable.setResults(
                    Map.of(
                        "job",
                        mapper.convertValue(job, typeRef),
                        "pods",
                        pods != null ? mapper.convertValue(pods, arrayRef) : new ArrayList<>()
                    )
                );
            } catch (IllegalArgumentException e) {
                log.error("error reading k8s results: {}", e.getMessage());
            }

            //collect logs, optional
            try {
                //TODO add sinceTime when available
                runnable.setLogs(framework.logs(job));
            } catch (K8sFrameworkException e1) {
                log.error("error collecting logs for job {}: {}", runnable.getId(), e1.getMessage());
            }

            //collect metrics, optional
            try {
                runnable.setMetrics(framework.metrics(job));
            } catch (K8sFrameworkException e1) {
                log.error("error collecting metrics for {}: {}", runnable.getId(), e1.getMessage());
            }
        } catch (K8sFrameworkException e) {
            // Set Runnable to ERROR state
            runnable.setState(K8sRunnableState.ERROR.name());
            runnable.setError(e.toError());
        }

        return runnable;
    }
}
