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

import it.smartcommunitylabdhub.commons.exceptions.FrameworkException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sBaseFramework;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnableState;
import it.smartcommunitylabdhub.framework.k8s.runnables.RunnableEventPublisher;
import it.smartcommunitylabdhub.lifecycle.locks.EntityLockException;
import it.smartcommunitylabdhub.lifecycle.locks.EntityLockManager;
import it.smartcommunitylabdhub.lifecycle.locks.EntityLockManager.EntityLock;
import it.smartcommunitylabdhub.lifecycle.locks.LocalEntityLockManager;
import it.smartcommunitylabdhub.runtimes.events.RunnableChangedEvent;
import it.smartcommunitylabdhub.runtimes.events.RunnableListener;
import it.smartcommunitylabdhub.runtimes.store.RunnableStore;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;
import org.springframework.util.Assert;

@Slf4j
public abstract class K8sRunnableListener<
    R extends K8sRunnable
> implements RunnableListener<R>, ResolvableTypeProvider {

    private final Class<R> clazz;

    private final K8sBaseFramework<R, ?> k8sFramework;

    private final RunnableStore<R> runnableStore;

    private RunnableEventPublisher eventPublisher;

    //per-runnable turn, shared with the monitor (keyed by runnable type + id)
    private EntityLockManager lockManager = LocalEntityLockManager.shared();

    @SuppressWarnings("unchecked")
    protected K8sRunnableListener(K8sBaseFramework<R, ?> k8sFramework, RunnableStore<R> runnableStore) {
        Assert.notNull(k8sFramework, "k8sFramework can not be null");
        Assert.notNull(runnableStore, "runnableStore can not be null");

        this.k8sFramework = k8sFramework;
        this.runnableStore = runnableStore;

        this.clazz = (Class<R>) runnableStore.getResolvableType().resolve();
        log.debug("started listener for {} with framework {}", clazz.getName(), k8sFramework.getClass().getName());
    }

    @Autowired(required = false)
    public void setLockManager(EntityLockManager lockManager) {
        if (lockManager != null) {
            this.lockManager = lockManager;
        }
    }

    @Autowired
    public void setEventPublisher(RunnableEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void receive(R runnable) {
        if (runnable != null) {
            process(runnable);
        }
    }

    public void process(R runnable) {
        Assert.notNull(runnable, "runnable can not be null");
        Assert.hasText(runnable.getId(), "runnable id can not be null or empty");
        log.info(
            "Receive runnable {} for execution: {} state {}",
            clazz.getSimpleName(),
            runnable.getId(),
            runnable.getState()
        );

        if (log.isTraceEnabled()) {
            log.trace("runnable {}: {}", clazz.getSimpleName(), runnable);
        }

        String id = runnable.getId();
        String framework = runnable.getFramework();
        String state = runnable.getState();

        EntityLock lock;
        try {
            lock = lockManager.acquire(clazz, id);
        } catch (EntityLockException e) {
            log.warn("Unable to acquire lock for runnable {} {}, skipping state {}", clazz.getSimpleName(), id, state);
            return;
        }

        try {
            //load runnable from store to ensure we have the latest version, and to check if it exists
            R stored = null;
            try {
                stored = runnableStore.find(id);
            } catch (StoreException e) {
                log.error("Error loading runnable {} {} from store: {}", clazz.getSimpleName(), id, e.getMessage());
            }

            //if present and state is different update to new state now to avoid concurrency, we'll sync in finally
            if (stored != null) {
                if (!state.equals(stored.getState())) {
                    log.debug("update state for runnable {} {} to {}", clazz.getSimpleName(), id, state);
                    stored.setState(state);
                    // try {
                    //     runnableStore.store(id, stored);
                    // } catch (StoreException e) {
                    //     log.error(
                    //         "Error updating state for runnable {} {} in store: {}",
                    //         clazz.getSimpleName(),
                    //         id,
                    //         e.getMessage()
                    //     );
                    // }
                }
            } else {
                log.warn(
                    "runnable {} {} not found in store, processing with provided instance",
                    clazz.getSimpleName(),
                    id
                );
                stored = runnable;
            }

            try {
                //handle supported operations with framework
                runnable = switch (K8sRunnableState.valueOf(state)) {
                    case K8sRunnableState.READY -> {
                        //READY needs the new runnable
                        //sanity check: reset left-over messages
                        runnable.setMessage(null);
                        yield k8sFramework.run(runnable);
                    }
                    case K8sRunnableState.STOP -> {
                        //stop on old if present
                        if (stored != null) {
                            runnable = stored;
                            runnable.setState(state);
                        }

                        //sanity check: reset left-over messages
                        runnable.setMessage(null);

                        yield k8sFramework.stop(runnable);
                    }
                    // case K8sRunnableState.RESUME -> {
                    //     //resume on old if present
                    //     if (stored != null) {
                    //         runnable = stored;
                    //         runnable.setState(state);
                    //     }

                    //     //sanity check: reset left-over messages
                    //     runnable.setMessage(null);
                    //     //TODO drop
                    //     yield k8sFramework.resume(runnable);
                    // }
                    case K8sRunnableState.DELETING -> {
                        //delete on old if present
                        if (stored != null) {
                            runnable = stored;
                            runnable.setState(state);
                        }

                        //sanity check: reset left-over messages
                        runnable.setMessage(null);

                        //mark as DELETING in store before acting: the monitor waits for the resources to be gone
                        try {
                            runnableStore.store(id, runnable);
                        } catch (StoreException e) {
                            log.error("Error storing runnable {} {}: {}", clazz.getSimpleName(), id, e.getMessage());
                        }

                        yield k8sFramework.delete(runnable);
                    }
                    default -> {
                        //return null as no action is taken for unsupported states so no output is generated
                        if (log.isTraceEnabled()) {
                            log.trace("no action for runnable {} {} state {}", clazz.getSimpleName(), id, state);
                        }
                        yield null;
                    }
                };

                if (runnable != null) {
                    //sanity check: id+framework can not change
                    if (!id.equals(runnable.getId()) || !framework.equals(runnable.getFramework())) {
                        throw new IllegalArgumentException("id mismatch");
                    }

                    if (log.isTraceEnabled()) {
                        log.trace("runnable result from framework {}: {}", clazz.getSimpleName(), runnable);
                    }
                }
            } catch (FrameworkException e) {
                // Set runnable to error state send event
                log.error("Error with k8s for runnable {} {}: {}", clazz.getSimpleName(), id, e.getMessage());
                if (runnable != null) {
                    runnable.setState(K8sRunnableState.ERROR.name());
                    runnable.setError(clazz.getSimpleName() + ":" + String.valueOf(e.getMessage()));

                    if (e instanceof K8sFrameworkException) {
                        runnable.setError(((K8sFrameworkException) e).toError());
                    }
                }
            } catch (RuntimeException e) {
                // Set runnable to error state send event
                log.error("Error for runnable {} {}: {}", clazz.getSimpleName(), id, e.getMessage());
                if (runnable != null) {
                    runnable.setState(K8sRunnableState.ERROR.name());
                    runnable.setError(String.valueOf(e.getMessage()));
                }
            } finally {
                if (runnable != null) {
                    try {
                        log.debug("update runnable {} {} in store", clazz.getSimpleName(), id);
                        //runnables shouldn't have a transient state at this point, but we can have a state change in case of error, so we update the store with the new state
                        //DELETING is kept: the monitor confirms when the resources are gone
                        if (runnable.isTransient() && !K8sRunnableState.DELETING.name().equals(runnable.getState())) {
                            log.warn(
                                "runnable {} {} in transient state {}, updating to non-transient state {}",
                                clazz.getSimpleName(),
                                id,
                                runnable.getState(),
                                K8sRunnableState.ERROR.name()
                            );
                            runnable.setState(K8sRunnableState.ERROR.name());
                        }

                        //if runnable state is final, remove from store, otherwise update
                        if (runnable.isFinal()) {
                            log.debug("delete run {} with state {}", runnable.getId(), runnable.getState());
                            runnableStore.remove(id);
                        } else {
                            log.debug("store run {} with state {}", runnable.getId(), runnable.getState());
                            runnableStore.store(id, runnable);
                        }
                    } catch (StoreException se) {
                        log.error("Error with store: {}", se.getMessage());
                    }

                    log.debug("Processed runnable {} {}", clazz.getSimpleName(), id);

                    if (eventPublisher != null) {
                        log.debug("Publish runnable {} {}", clazz.getSimpleName(), id);

                        RunnableChangedEvent<RunRunnable> event = RunnableChangedEvent.build(runnable, state);

                        if (log.isTraceEnabled()) {
                            log.trace("runnable {} {} event {}", clazz.getSimpleName(), id, event);
                        }

                        // Publish event to Run Manager
                        eventPublisher.publishEvent(event);
                    }
                }
            }
        } finally {
            lock.close();
        }
    }

    public ResolvableType getResolvableType() {
        return ResolvableType.forClass(clazz);
    }
}
