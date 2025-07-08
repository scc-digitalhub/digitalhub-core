/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.argo.listeners;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.framework.argo.infrastructure.k8s.K8sArgoWorkflowFramework;
import it.smartcommunitylabdhub.framework.argo.runnables.K8sArgoWorkflowRunnable;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
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
public class K8sArgoWorkflowRunnableListener {

    @Autowired
    K8sArgoWorkflowFramework argoFramework;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    private RunnableStore<K8sArgoWorkflowRunnable> runnableStore;

    @Async
    @EventListener
    public void listen(K8sArgoWorkflowRunnable runnable) {
        Assert.notNull(runnable, "runnable can not be null");
        Assert.hasText(runnable.getId(), "runnable id can not be null or empty");
        log.info("Receive runnable for execution: {}", runnable.getId());

        String state = runnable.getState();

        try {
            runnable =
                switch (State.valueOf(state)) {
                    case State.READY -> {
                        yield argoFramework.run(runnable);
                    }
                    case State.STOP -> {
                        yield argoFramework.stop(runnable);
                    }
                    case State.DELETING -> {
                        yield argoFramework.delete(runnable);
                    }
                    default -> {
                        yield null;
                    }
                };

            if (runnable != null) {
                try {
                    // If runnable is deleted, remove from store
                    if (runnable.getState().equals(State.DELETED.name())) {
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
            runnable.setState(State.ERROR.name());
            runnable.setError(e.toError());

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
