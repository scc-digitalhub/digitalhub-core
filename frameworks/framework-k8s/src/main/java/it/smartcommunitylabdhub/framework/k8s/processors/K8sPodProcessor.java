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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.models.V1Pod;
import it.smartcommunitylabdhub.commons.annotations.common.ProcessorType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.status.Status;
import it.smartcommunitylabdhub.framework.k8s.jackson.KubernetesMapper;
import it.smartcommunitylabdhub.framework.k8s.model.K8sPodStatus;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@ProcessorType(
    stages = { "onPending", "onRunning", "onCompleted", "onError", "onStopped" },
    type = Run.class,
    spec = Status.class
)
@Component
@Slf4j
public class K8sPodProcessor implements Processor<Run, K8sPodStatus> {

    protected static final ObjectMapper mapper = KubernetesMapper.OBJECT_MAPPER;
    protected static final TypeReference<ArrayList<V1Pod>> arrayRef = new TypeReference<ArrayList<V1Pod>>() {};

    @Override
    public <I> K8sPodStatus process(String stage, Run run, I input) throws CoreRuntimeException {
        if (input instanceof K8sRunnable) {
            Map<String, Serializable> res = ((K8sRunnable) input).getResults();

            try {
                if (res != null && !res.isEmpty()) {
                    //fetch pod details when available
                    if (res.containsKey("pods")) {
                        K8sPodStatus status = new K8sPodStatus();
                        List<V1Pod> pods = mapper.convertValue(res.get("pods"), arrayRef);
                        List<Serializable> list = new ArrayList<>();
                        pods.forEach(pod -> {
                            HashMap<String, Serializable> podMap = new HashMap<>();
                            podMap.put("name", pod.getMetadata() != null ? pod.getMetadata().getName() : null);
                            podMap.put(
                                "namespace",
                                pod.getMetadata() != null ? pod.getMetadata().getNamespace() : null
                            );

                            if (pod.getStatus() != null) {
                                podMap.put("phase", pod.getStatus().getPhase());

                                if (pod.getStatus().getConditions() != null) {
                                    podMap.put(
                                        "conditions",
                                        pod
                                            .getStatus()
                                            .getConditions()
                                            .stream()
                                            .map(condition -> {
                                                Map<String, Serializable> c = new HashMap<>();
                                                c.put("type", condition.getType());
                                                c.put("status", condition.getStatus());
                                                c.put(
                                                    "lastTransitionTime",
                                                    condition.getLastTransitionTime() != null
                                                        ? condition.getLastTransitionTime().toString()
                                                        : null
                                                );
                                                c.put("reason", condition.getReason());
                                                c.put("message", condition.getMessage());
                                                return c;
                                            })
                                            .collect(Collectors.toCollection(ArrayList::new))
                                    );
                                }

                                podMap.put(
                                    "startTime",
                                    pod.getStatus().getStartTime() != null
                                        ? pod.getStatus().getStartTime().toString()
                                        : null
                                );

                                if (pod.getStatus().getContainerStatuses() != null) {
                                    podMap.put(
                                        "containers",
                                        pod
                                            .getStatus()
                                            .getContainerStatuses()
                                            .stream()
                                            .map(s -> {
                                                Map<String, Serializable> c = new HashMap<>();
                                                c.put("name", s.getName());
                                                c.put("ready", s.getReady());
                                                c.put("restartCount", s.getRestartCount());
                                                c.put("image", s.getImage());
                                                if (s.getState() != null) {
                                                    if (s.getState().getRunning() != null) {
                                                        c.put(
                                                            "state",
                                                            "running since " +
                                                            (s.getState().getRunning().getStartedAt() != null
                                                                    ? s
                                                                        .getState()
                                                                        .getRunning()
                                                                        .getStartedAt()
                                                                        .toString()
                                                                    : "unknown")
                                                        );
                                                    } else if (s.getState().getTerminated() != null) {
                                                        c.put(
                                                            "state",
                                                            "terminated at " +
                                                            (s.getState().getTerminated().getFinishedAt() != null
                                                                    ? s
                                                                        .getState()
                                                                        .getTerminated()
                                                                        .getFinishedAt()
                                                                        .toString()
                                                                    : "unknown") +
                                                            " exitCode: " +
                                                            s.getState().getTerminated().getExitCode() +
                                                            " reason: " +
                                                            String.valueOf(s.getState().getTerminated().getReason())
                                                        );
                                                    } else if (s.getState().getWaiting() != null) {
                                                        c.put(
                                                            "state",
                                                            "waiting: " + s.getState().getWaiting().getReason()
                                                        );
                                                    }
                                                }
                                                return c;
                                            })
                                            .collect(Collectors.toCollection(ArrayList::new))
                                    );
                                }
                            }
                            list.add(podMap);
                        });

                        status.setPods(list);

                        return status;
                    }
                }
            } catch (Exception e) {
                log.error("error processing k8s pod details: {}", e.getMessage());
            }
        }
        return null;
    }
}
