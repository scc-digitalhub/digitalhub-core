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
import it.smartcommunitylabdhub.commons.exceptions.FrameworkException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sBaseFramework;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnableState;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sContainerBuilderRunnable;
import it.smartcommunitylabdhub.runtimes.events.RunnableChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@ConditionalOnKubernetes
@Slf4j
public class K8sContainerBuilderRunnableListener {

    @Autowired(required = false)
    K8sBaseFramework<K8sContainerBuilderRunnable, V1Job> k8sFramework;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    private RunnableStore<K8sContainerBuilderRunnable> runnableStore;

    @Async
    @EventListener
    public void listen(K8sContainerBuilderRunnable runnable) {
        Assert.notNull(runnable, "runnable can not be null");
        Assert.hasText(runnable.getId(), "runnable id can not be null or empty");
        log.info("Receive runnable for execution: {}", runnable.getId());

        if (k8sFramework == null) {
            log.error("No builder framework available for runnable {}", runnable.getId());
            runnable.setState(K8sRunnableState.ERROR.name());
            runnable.setError("No builder framework available");
            try {
                runnableStore.store(runnable.getId(), runnable);
            } catch (StoreException e) {
                log.error("Error with store: {}", e.getMessage());
            }
            return;
        }

        String state = runnable.getState();

        try {
            runnable =
                switch (K8sRunnableState.valueOf(state)) {
                    case K8sRunnableState.READY -> {
                        yield k8sFramework.run(runnable);
                    }
                    case K8sRunnableState.STOP -> {
                        yield k8sFramework.stop(runnable);
                    }
                    case K8sRunnableState.DELETING -> {
                        yield k8sFramework.delete(runnable);
                    }
                    default -> {
                        yield null;
                    }
                };

            if (runnable != null) {
                try {
                    // If runnable is deleted, remove from store
                    if (runnable.getState().equals(K8sRunnableState.DELETED.name())) {
                        runnableStore.remove(runnable.getId());
                    } else {
                        runnableStore.store(runnable.getId(), runnable);
                    }
                } catch (StoreException e) {
                    log.error("Error with store: {}", e.getMessage());
                }
            }
        } catch (K8sFrameworkException e) {
            // Set runnable to error state send event
            log.error("Error with k8s: {}", e.getMessage());
            runnable.setState(K8sRunnableState.ERROR.name());
            runnable.setError(e.toError());

            try {
                runnableStore.store(runnable.getId(), runnable);
            } catch (StoreException ex) {
                log.error("Error with store: {}", ex.getMessage());
            }
        } catch (FrameworkException e) {
            // Set runnable to error state send event
            log.error("Error with k8s: {}", e.getMessage());
            runnable.setState(K8sRunnableState.ERROR.name());
            runnable.setError(e.getMessage());

            try {
                runnableStore.store(runnable.getId(), runnable);
            } catch (StoreException ex) {
                log.error("Error with store: {}", ex.getMessage());
            }
        } finally {
            if (runnable != null) {
                log.debug("Processed runnable {}", runnable.getId());

                // Publish event to Run Manager
                eventPublisher.publishEvent(RunnableChangedEvent.build(runnable, state));
            }
        }
    }
}
