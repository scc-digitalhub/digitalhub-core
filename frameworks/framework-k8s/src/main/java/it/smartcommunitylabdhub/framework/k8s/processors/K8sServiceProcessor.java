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

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.openapi.models.V1ServiceStatus;
import it.smartcommunitylabdhub.commons.annotations.common.ProcessorType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.status.Status;
import it.smartcommunitylabdhub.framework.k8s.jackson.KubernetesMapper;
import it.smartcommunitylabdhub.framework.k8s.model.K8sServiceInfo;
import it.smartcommunitylabdhub.framework.k8s.model.K8sServiceStatus;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreServiceType;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@ProcessorType(stages = { "onRunning" }, type = Run.class, spec = Status.class)
@Component
@Slf4j
public class K8sServiceProcessor implements Processor<Run, K8sServiceStatus> {

    @Override
    public <I> K8sServiceStatus process(String stage, Run run, I input) throws CoreRuntimeException {
        if (input instanceof K8sRunnable) {
            Map<String, Serializable> res = ((K8sRunnable) input).getResults();
            //extract k8s details for svc
            //note: build only if missing, we don't update service info
            if (res != null && res.containsKey("service") && run.getStatus().get("service") == null) {
                try {
                    Map<String, Serializable> s = (Map<String, Serializable>) res.get("service");
                    V1Service service = KubernetesMapper.OBJECT_MAPPER.convertValue(s, V1Service.class);

                    if (
                        service.getMetadata() == null ||
                        service.getSpec() == null ||
                        service.getSpec().getPorts() == null
                    ) {
                        //missing info
                        return null;
                    }

                    log.debug("extract info for service {}", String.valueOf(service.getMetadata().getName()));

                    K8sServiceInfo.K8sServiceInfoBuilder builder = K8sServiceInfo.builder();

                    //metadata
                    V1ObjectMeta metadata = service.getMetadata();
                    builder.name(metadata.getName()).namespace(metadata.getNamespace());

                    //spec
                    V1ServiceSpec spec = service.getSpec();
                    builder.type(spec.getType()).externalIps(spec.getExternalIPs()).ports(spec.getPorts());

                    //status
                    String type = spec.getType();

                    if (CoreServiceType.ClusterIP.name().equals(type) && !spec.getPorts().isEmpty()) {
                        //add ip
                        builder.clusterIP(spec.getClusterIP());

                        //public url is first port exposed
                        V1ServicePort port = spec.getPorts().getFirst();
                        String url = String.format(
                            "%s.%s:%d",
                            metadata.getName(),
                            metadata.getNamespace(),
                            port.getPort()
                        );
                        builder.url(url);
                        log.debug("service {} clusterIp url {}", metadata.getName(), url);

                        //expose all urls if more than 1 port
                        if (spec.getPorts().size() > 1) {
                            List<String> urls = spec
                                .getPorts()
                                .stream()
                                .map(p ->
                                    String.format("%s.%s:%d", metadata.getName(), metadata.getNamespace(), p.getPort())
                                )
                                .toList();

                            builder.urls(urls);
                        }
                    } else if (CoreServiceType.NodePort.name().equals(type) && !spec.getPorts().isEmpty()) {
                        //add ip
                        builder.clusterIP(spec.getClusterIP());

                        //public url is first port exposed
                        V1ServicePort port = spec.getPorts().getFirst();
                        String url = String.format(
                            "%s.%s:%d",
                            metadata.getName(),
                            metadata.getNamespace(),
                            port.getPort()
                        );
                        builder.url(url);
                        log.debug("service {} nodePort url {}", metadata.getName(), url);

                        //expose all urls if more than 1 port
                        if (spec.getPorts().size() > 1) {
                            List<String> urls = spec
                                .getPorts()
                                .stream()
                                .map(p ->
                                    String.format("%s.%s:%d", metadata.getName(), metadata.getNamespace(), p.getPort())
                                )
                                .toList();

                            builder.urls(urls);
                        }
                    } else if (CoreServiceType.LoadBalancer.name().equals(type) && !spec.getPorts().isEmpty()) {
                        //add ip
                        builder.clusterIP(spec.getClusterIP());

                        //if loadbalancer is in status we can build url
                        Optional
                            .ofNullable(service.getStatus())
                            .map(V1ServiceStatus::getLoadBalancer)
                            .map(l -> l.getIngress())
                            .map(i -> i.getFirst())
                            .map(i -> i.getIp() != null ? i : null)
                            .ifPresent(ingress -> {
                                //set ip and host
                                builder.ip(ingress.getIp()).hostname(ingress.getHostname());
                                String host = StringUtils.hasText(ingress.getHostname())
                                    ? ingress.getHostname()
                                    : ingress.getIp();

                                //public url is first port exposed
                                V1ServicePort port = spec.getPorts().getFirst();
                                String url = String.format("%s:%d", host, port.getPort());
                                builder.url(url);
                                log.debug("service {} loadBalancer url {}", metadata.getName(), url);

                                //expose all urls if more than 1 port
                                if (spec.getPorts().size() > 1) {
                                    List<String> urls = spec
                                        .getPorts()
                                        .stream()
                                        .map(p -> String.format("%s:%d", host, p.getPort()))
                                        .toList();

                                    builder.urls(urls);
                                }
                            });
                    } else if (CoreServiceType.ExternalName.name().equals(type) && !spec.getPorts().isEmpty()) {
                        //add ip
                        builder.clusterIP(spec.getClusterIP());

                        //expose name
                        String externalName = spec.getExternalName();
                        builder.externalName(externalName);

                        //public url is first port exposed
                        V1ServicePort port = spec.getPorts().getFirst();
                        String url = String.format("%s:%d", externalName, port.getPort());
                        builder.url(url);
                        log.debug("service {} externalName url {}", metadata.getName(), url);

                        //expose all urls if more than 1 port
                        if (spec.getPorts().size() > 1) {
                            List<String> urls = spec
                                .getPorts()
                                .stream()
                                .map(p -> String.format("%s:%d", externalName, p.getPort()))
                                .toList();

                            builder.urls(urls);
                        }
                    }

                    return K8sServiceStatus.builder().service(builder.build()).build();
                } catch (ClassCastException | NullPointerException e) {
                    //invalid definition, skip
                }
            }
        }

        return null;
    }
}
