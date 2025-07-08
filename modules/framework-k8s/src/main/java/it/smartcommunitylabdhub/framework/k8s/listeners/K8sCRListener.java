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

package it.smartcommunitylabdhub.framework.k8s.listeners;

import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sCRFramework;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

public class K8sCRListener extends K8sRunnableListener<K8sCRRunnable> {

    public K8sCRListener(K8sCRFramework k8sFramework, RunnableStore<K8sCRRunnable> runnableStore) {
        super(k8sFramework, runnableStore);
    }

    @Async
    @EventListener
    public void listen(K8sCRRunnable runnable) {
        if (runnable != null) {
            //clone to fully detach
            process(runnable.toBuilder().build());
        }
    }
}
