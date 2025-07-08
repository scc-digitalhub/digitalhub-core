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

package it.smartcommunitylabdhub.framework.k8s.infrastructure.monitor;

import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1Pod;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.MonitorComponent;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sJobFramework;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Slf4j
@ConditionalOnKubernetes
@Component
@MonitorComponent(framework = K8sJobFramework.FRAMEWORK)
public class K8sJobMonitor extends K8sBaseMonitor<K8sJobRunnable> {

    private final K8sJobFramework framework;

    public K8sJobMonitor(RunnableStore<K8sJobRunnable> runnableStore, K8sJobFramework k8sJobFramework) {
        super(runnableStore);
        Assert.notNull(k8sJobFramework, "job framework is required");

        this.framework = k8sJobFramework;
    }

    @Override
    public K8sJobRunnable refresh(K8sJobRunnable runnable) {
        try {
            log.debug("load job for {}", runnable.getId());
            V1Job job = framework.get(framework.build(runnable));

            if (job == null || job.getStatus() == null) {
                // something is missing, no recovery
                log.error("Missing or invalid job for {}", runnable.getId());
                runnable.setState(State.ERROR.name());
                runnable.setError("Job missing or invalid");
            }

            if (log.isTraceEnabled()) {
                log.trace("Job status: {}", job.getStatus().toString());
            }

            //TODO evaluate target for succeded/failed
            if (job.getStatus().getSucceeded() != null && job.getStatus().getSucceeded().intValue() > 0) {
                // Job has succeeded
                log.debug("Job status succeeded for {}", runnable.getId());
                runnable.setState(State.COMPLETED.name());
            } else if (job.getStatus().getFailed() != null && job.getStatus().getFailed().intValue() > 0) {
                // Job has failed delete job and pod
                log.debug("Job status failed for {}", runnable.getId());
                runnable.setState(State.ERROR.name());
                runnable.setError("Job failed: " + job.getStatus().getFailed());
            }

            //also evaluate condition for failure
            if (job.getStatus().getConditions() != null) {
                for (V1JobCondition condition : job.getStatus().getConditions()) {
                    if (condition.getType().equals("Failed")) {
                        log.debug("Job condition failed for {}", runnable.getId());
                        runnable.setState(State.ERROR.name());
                        runnable.setError("Job condition failed: " + condition.getMessage());
                        break;
                    }
                }
            }

            //try to fetch pods
            List<V1Pod> pods = null;
            try {
                log.debug("Collect pods for job {} for run {}", job.getMetadata().getName(), runnable.getId());
                pods = framework.pods(job);
            } catch (K8sFrameworkException e1) {
                log.error("error collecting pods for job {}: {}", runnable.getId(), e1.getMessage());
            }

            if (!"disable".equals(collectResults)) {
                //update results
                try {
                    runnable.setResults(
                        MapUtils.mergeMultipleMaps(
                            runnable.getResults(),
                            Map.of(
                                "job",
                                mapper.convertValue(job, typeRef),
                                "pods",
                                pods != null ? mapper.convertValue(pods, arrayRef) : new ArrayList<>()
                            )
                        )
                    );
                } catch (IllegalArgumentException e) {
                    log.error("error reading k8s results: {}", e.getMessage());
                }
            }

            if (Boolean.TRUE.equals(collectLogs)) {
                //collect logs, optional
                try {
                    log.debug("Collect logs for job {} for run {}", job.getMetadata().getName(), runnable.getId());
                    //TODO add sinceTime when available
                    runnable.setLogs(framework.logs(job));
                } catch (K8sFrameworkException e1) {
                    log.error("error collecting logs for job {}: {}", runnable.getId(), e1.getMessage());
                }
            }

            if (Boolean.TRUE.equals(collectMetrics)) {
                //collect metrics, optional
                try {
                    log.debug("Collect metrics for job {} for run {}", job.getMetadata().getName(), runnable.getId());
                    runnable.setMetrics(framework.metrics(job));
                } catch (K8sFrameworkException e1) {
                    log.error("error collecting metrics for {}: {}", runnable.getId(), e1.getMessage());
                }
            }
        } catch (K8sFrameworkException e) {
            // Set Runnable to ERROR state
            runnable.setState(State.ERROR.name());
            runnable.setError(e.toError());
        }

        return runnable;
    }
}
