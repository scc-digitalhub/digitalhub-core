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

package it.smartcommunitylabdhub.framework.kaniko.listeners;

import io.kubernetes.client.openapi.models.V1Job;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sBaseFramework;
import it.smartcommunitylabdhub.framework.k8s.listeners.K8sRunnableListener;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sContainerBuilderRunnable;
import it.smartcommunitylabdhub.runtimes.store.RunnableStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnKubernetes
@Slf4j
public class K8sContainerBuilderRunnableListener extends K8sRunnableListener<K8sContainerBuilderRunnable> {

    public K8sContainerBuilderRunnableListener(
        K8sBaseFramework<K8sContainerBuilderRunnable, V1Job> k8sFramework,
        RunnableStore<K8sContainerBuilderRunnable> runnableStore
    ) {
        super(k8sFramework, runnableStore);
    }

    @Async
    @EventListener
    public void listen(K8sContainerBuilderRunnable runnable) {
        if (runnable != null) {
            process(runnable);
        }
    }
}
