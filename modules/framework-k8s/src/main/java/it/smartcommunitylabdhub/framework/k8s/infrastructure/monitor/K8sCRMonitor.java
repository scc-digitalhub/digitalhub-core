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

import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.MonitorComponent;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sCRFramework;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
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
                runnable.setState(State.ERROR.name());
                runnable.setError("CR missing or invalid");
            } else {
                try {
                    //explicit conversion
                    //NOTE: dko contains a GSON JsonObject which jackson *can not* convert
                    HashMap<String, Serializable> spec = K8sCRFramework.jsonElementToSpec(cr.getRaw());

                    runnable.setResults(MapUtils.mergeMultipleMaps(runnable.getResults(), Map.of(cr.getKind(), spec)));
                } catch (IOException e) {
                    log.error("Error reading spec from raw", e);
                }
            }
            // TODO?

        } catch (K8sFrameworkException e) {
            // Set Runnable to ERROR state
            runnable.setState(State.ERROR.name());
            runnable.setError(e.toError());
        }

        return runnable;
    }
}
