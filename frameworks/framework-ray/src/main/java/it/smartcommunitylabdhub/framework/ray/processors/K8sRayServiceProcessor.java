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

package it.smartcommunitylabdhub.framework.ray.processors;

import io.kubernetes.client.openapi.models.V1ServicePort;
import it.smartcommunitylabdhub.commons.annotations.common.ProcessorType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.models.status.Status;
import it.smartcommunitylabdhub.framework.k8s.model.K8sServiceDetails;
import it.smartcommunitylabdhub.framework.k8s.model.K8sServiceInfo;
import it.smartcommunitylabdhub.framework.k8s.model.K8sServiceInfo.K8sServiceInfoBuilder;
import it.smartcommunitylabdhub.framework.k8s.model.K8sServiceStatus;
import it.smartcommunitylabdhub.framework.k8s.objects.AppProtocol;
import it.smartcommunitylabdhub.framework.ray.runnables.K8sRayRunnable;
import it.smartcommunitylabdhub.runs.Run;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@ProcessorType(stages = { "onRunning" }, type = Run.class, spec = Status.class)
@Component
@Slf4j
public class K8sRayServiceProcessor implements Processor<Run, K8sServiceStatus> {

    @SuppressWarnings("unchecked")
    @Override
    public K8sServiceStatus process(String stage, Run run, Serializable input) throws CoreRuntimeException {
        // // service status

        if (input instanceof K8sRayRunnable k8sRunnable) {
            Map<String, Serializable> res = k8sRunnable.getResults();
            if (res != null && run.getStatus() != null && run.getStatus().get("service") == null) {
                Map<String, Serializable> rayJob = (Map<String, Serializable>) res.get("RayJob");
                if (rayJob != null) {
                    Map<String, Serializable> status = (Map<String, Serializable>) rayJob.get("status");
                    Map<String, Serializable> metadata = (Map<String, Serializable>) rayJob.get("metadata");
                    if (status != null && metadata != null) {
                        Map<String, Serializable> rayClusterStatus = (Map<String, Serializable>) status.get(
                            "rayClusterStatus"
                        );
                        if (rayClusterStatus != null) {
                            // process rayClusterStatus if needed
                            Map<String, Serializable> head = (Map<String, Serializable>) rayClusterStatus.get("head");
                            Map<String, String> endpoints = (Map<String, String>) rayClusterStatus.get("endpoints");

                            if (head != null && endpoints != null) {
                                K8sServiceInfoBuilder serviceInfoBuilder = K8sServiceInfo.builder();
                                serviceInfoBuilder.name(
                                    head.get("serviceName") != null ? head.get("serviceName").toString() : null
                                );
                                serviceInfoBuilder.ip(
                                    head.get("serviceIP") != null ? head.get("serviceIP").toString() : null
                                );
                                List<V1ServicePort> ports = new LinkedList<>();
                                List<K8sServiceDetails> urls = new LinkedList<>();
                                for (Map.Entry<String, String> entry : endpoints.entrySet()) {
                                    V1ServicePort port = new V1ServicePort();
                                    port.setName(entry.getKey());
                                    port.setPort(Integer.parseInt(entry.getValue()));
                                    ports.add(port);
                                    String url = String.format("%s.%s:%s", metadata.get("name"), metadata.get("namespace"), entry.getValue());
                                    String protocol = AppProtocol.http.name();
                                    if ("dashboard".equals(entry.getKey())) {
                                        serviceInfoBuilder.url(url);
                                        protocol= AppProtocol.www.name();
                                    }
                                    urls.add(new K8sServiceDetails(url, 0, protocol));
                                }
                                if (!urls.isEmpty()) {
                                    serviceInfoBuilder.urls(urls);
                                }

                                serviceInfoBuilder.ports(ports);

                                K8sServiceStatus serviceStatus = K8sServiceStatus.builder()
                                    .service(serviceInfoBuilder.build())
                                    .build();
                                return serviceStatus;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }
}
