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

import io.kubernetes.client.openapi.models.EventsV1Event;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.MonitorComponent;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sCRFramework;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnableState;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Slf4j
@ConditionalOnKubernetes
@Component
@MonitorComponent(framework = K8sCRFramework.FRAMEWORK)
public class K8sCRMonitor extends K8sBaseMonitor<K8sCRRunnable> {

    private final K8sCRFramework framework;

    public K8sCRMonitor(RunnableStore<K8sCRRunnable> runnableStore, K8sCRFramework crFramework) {
        super(runnableStore);
        Assert.notNull(crFramework, "CR framework is required");

        this.framework = crFramework;
    }

    @Override
    public K8sCRRunnable refresh(K8sCRRunnable runnable) {
        try {
            DynamicKubernetesApi dynamicApi = framework.getDynamicKubernetesApi(runnable);
            DynamicKubernetesObject cr = framework.get(framework.build(runnable), dynamicApi);

            // check status
            // if ERROR signal, otherwise let RUNNING
            if (cr == null) {
                // something is missing, no recovery
                log.error("Missing or invalid CR for {}", runnable.getId());
                runnable.setState(K8sRunnableState.ERROR.name());
                runnable.setError("CR missing or invalid");
            } else {
                //if it's there, it's running
                runnable.setState(K8sRunnableState.RUNNING.name());

                try {
                    //explicit conversion
                    //NOTE: dko contains a GSON JsonObject which jackson *can not* convert
                    HashMap<String, Serializable> spec = K8sCRFramework.jsonElementToSpec(cr.getRaw());

                    runnable.setResults(MapUtils.mergeMultipleMaps(runnable.getResults(), Map.of(cr.getKind(), spec)));
                } catch (IOException e) {
                    log.error("Error reading spec from raw", e);
                }
            }

            //fetch events
            List<EventsV1Event> events = null;
            try {
                events = framework.events(cr);
                if (events != null) {
                    log.debug("Fetched {} events for CR {}", events.size(), runnable.getId());
                    runnable.setEvents(new ArrayList<>(mapper.convertValue(events, arrayRef)));
                } else {
                    log.debug("No events found for CR {}", runnable.getId());
                }
            } catch (IllegalArgumentException e) {
                log.error("error reading k8s events: {}", e.getMessage());
            }

            //try to fetch pods
            List<V1Pod> pods = null;
            try {
                pods = framework.pods(cr);

                //collect events for pods as well
                if (pods != null) {
                    events = new ArrayList<>(events != null ? events : new ArrayList<>());
                    for (V1Pod pod : pods) {
                        try {
                            List<EventsV1Event> podEvents = framework.events(pod);
                            if (podEvents != null && !podEvents.isEmpty()) {
                                log.debug("Adding {} events for pod {}", podEvents.size(), pod.getMetadata().getName());
                                events.addAll(podEvents);
                            }
                        } catch (K8sFrameworkException e1) {
                            log.error(
                                "error collecting events for pod {}: {}",
                                pod.getMetadata().getName(),
                                e1.getMessage()
                            );
                        }
                    }
                }
            } catch (K8sFrameworkException e1) {
                log.error("error collecting pods for CR {}: {}", runnable.getId(), e1.getMessage());
            }

            if (events != null) {
                runnable.setEvents(new ArrayList<>(mapper.convertValue(events, arrayRef)));
            }
            //update results
            try {
                runnable.setResults(
                    MapUtils.mergeMultipleMaps(
                        runnable.getResults(),
                        Map.of("pods", pods != null ? mapper.convertValue(pods, arrayRef) : new ArrayList<>())
                    )
                );
            } catch (IllegalArgumentException e) {
                log.error("error reading k8s results: {}", e.getMessage());
            }

            //collect logs, optional
            try {
                // TODO add sinceTime when available
                // TODO read native argo logs
                runnable.setLogs(framework.logs(cr));
            } catch (K8sFrameworkException e1) {
                log.error("error collecting logs for {}: {}", runnable.getId(), e1.getMessage());
            }

            //collect metrics, optional
            try {
                runnable.setMetrics(framework.metrics(cr));
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
