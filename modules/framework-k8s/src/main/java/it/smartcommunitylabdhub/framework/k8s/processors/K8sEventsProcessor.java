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
import it.smartcommunitylabdhub.framework.k8s.model.K8sEventsStatus;
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
public class K8sEventsProcessor implements Processor<Run, K8sEventsStatus> {

    protected static final ObjectMapper mapper = KubernetesMapper.OBJECT_MAPPER;
    protected static final TypeReference<ArrayList<V1Pod>> arrayRef = new TypeReference<ArrayList<V1Pod>>() {};

    @Override
    public <I> K8sEventsStatus process(String stage, Run run, I input) throws CoreRuntimeException {
        if (input instanceof K8sRunnable) {
            List<Map<String, Serializable>> res = ((K8sRunnable) input).getEvents();

            try {
                if (res != null && !res.isEmpty()) {
                    //transform events into synthetic representation
                    K8sEventsStatus status = new K8sEventsStatus();
                    List<HashMap<String, Serializable>> events = res
                        .stream()
                        .map(e -> {
                            HashMap<String, Serializable> event = new HashMap<>();
                            event.put("type", e.get("type"));
                            event.put("reason", e.get("reason"));
                            event.put("note", e.get("note"));

                            if (e.get("metadata") instanceof Map) {
                                Map<String, Serializable> metadata = (Map<String, Serializable>) e.get("metadata");
                                event.put("timestamp", metadata.get("creationTimestamp"));
                            }

                            if (e.get("regarding") instanceof Map) {
                                Map<String, Serializable> regarding = (Map<String, Serializable>) e.get("regarding");
                                event.put("kind", regarding.get("kind"));
                                event.put("name", regarding.get("name"));
                            }

                            return event;
                        })
                        .collect(Collectors.toList());

                    status.setEvents(new ArrayList<>(events));

                    return status;
                }
            } catch (Exception e) {
                log.error("error processing k8s event details: {}", e.getMessage());
            }
        }
        return null;
    }
}
