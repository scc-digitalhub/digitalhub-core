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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.models.V1Pod;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sBaseFramework;
import it.smartcommunitylabdhub.framework.k8s.jackson.KubernetesMapper;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLog;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnableState;
import it.smartcommunitylabdhub.framework.k8s.runnables.RunnableEventPublisher;
import it.smartcommunitylabdhub.runtimes.events.RunnableChangedEvent;
import it.smartcommunitylabdhub.lifecycle.locks.EntityLockException;
import it.smartcommunitylabdhub.lifecycle.locks.EntityLockManager;
import it.smartcommunitylabdhub.lifecycle.locks.EntityLockManager.EntityLock;
import it.smartcommunitylabdhub.lifecycle.locks.LocalEntityLockManager;
import it.smartcommunitylabdhub.runtimes.store.RunnableStore;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;

@Slf4j
public abstract class K8sBaseMonitor<T extends K8sRunnable> implements Runnable {

    //custom object mapper with mixIn for IntOrString
    protected static final ObjectMapper mapper = KubernetesMapper.OBJECT_MAPPER;
    protected static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};
    protected static final TypeReference<ArrayList<HashMap<String, Serializable>>> arrayRef = new TypeReference<
        ArrayList<HashMap<String, Serializable>>
    >() {};

    protected final RunnableStore<T> store;
    private RunnableEventPublisher eventPublisher;

    //per-runnable turn, shared with the listener
    private EntityLockManager lockManager = LocalEntityLockManager.shared();

    protected Boolean collectLogs = Boolean.TRUE;
    protected Boolean collectMetrics = Boolean.TRUE;
    protected String collectResults = "default";

    protected K8sBaseMonitor(RunnableStore<T> runnableStore) {
        Assert.notNull(runnableStore, "runnable store is required");

        this.store = runnableStore;
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

    @Autowired
    public void setCollectLogs(@Value("${kubernetes.logs}") Boolean collectLogs) {
        this.collectLogs = collectLogs;
    }

    @Autowired
    public void setCollectMetrics(@Value("${kubernetes.metrics}") Boolean collectMetrics) {
        this.collectMetrics = collectMetrics;
    }

    @Autowired
    public void setCollectResults(@Value("${kubernetes.results}") String collectResults) {
        this.collectResults = collectResults;
    }

    @Override
    public void run() {
        monitor();
    }

    public void monitor() {
        log.debug("monitor all active...");

        //snapshot of the ids only: every runnable is re-read within its turn
        List<String> ids = store
            .findAll()
            .stream()
            .filter(r -> r.getState() != null && (!r.isTransient() || isDeleting(r)))
            .map(K8sRunnable::getId)
            .toList();

        for (String id : ids) {
            log.debug("monitor run {}", id);
            try {
                monitor(id);
            } catch (StoreException | RuntimeException e) {
                //never stop the loop for a single runnable
                log.error("Error monitoring runnable {}: {}", id, e.getMessage());
            }
        }

        log.debug("monitor completed.");
    }

    public void monitor(String id) throws StoreException {
        T refreshed;

        //turn shared with the listener: one operation at a time per runnable
        try (EntityLock lock = lockManager.acquire(lockType(), id)) {
            T runnable = store.find(id);
            if (runnable == null) {
                //nothing to do
                log.debug("runnable {} not found", id);
                return;
            }

            if (isDeleting(runnable)) {
                //deletion requested: wait for the resources to be gone
                refreshed = refreshDeleting(runnable);
                if (refreshed == null) {
                    return;
                }
            } else if (runnable.getState() != null && runnable.isTransient()) {
                //skip refresh for transient states, we don't wanna override an operation in progress
                log.debug("runnable {} in transient state {}, skipping refresh", id, runnable.getState());
                return;
            } else {
                //if final avoid refresh
                refreshed = runnable.isFinal() ? runnable : refresh(runnable);
            }

            if (log.isTraceEnabled()) {
                log.trace("refreshed: {}", refreshed);
            }

            // Update the runnable
            //if runnable state is final, remove from store, otherwise update
            if (refreshed.isFinal()) {
                log.debug("delete run {} with state {}", refreshed.getId(), refreshed.getState());
                store.remove(refreshed.getId());
            } else {
                log.debug("store run {} with state {}", refreshed.getId(), refreshed.getState());
                store.store(refreshed.getId(), refreshed);
            }
        } catch (EntityLockException e) {
            //busy (ex. the listener is acting on it): next cycle
            log.debug("runnable {} busy, skipping: {}", id, e.getMessage());
            return;
        } catch (StoreException e) {
            log.error("Error with runnable store: {}", e.getMessage());
            throw e;
        }

        //always publish, even if final. We expect receivers to be idempotent
        publish(refreshed);
    }

    /**
     * A runnable in DELETING becomes DELETED once its resources are gone. Still there, or unknown
     * (any failure other than missing): null, nothing changes.
     */
    protected T refreshDeleting(T runnable) {
        try {
            if (exists(runnable)) {
                log.debug("runnable {} still deleting", runnable.getId());
                return null;
            }

            log.debug("runnable {} resources gone, deleted", runnable.getId());
            runnable.setState(K8sRunnableState.DELETED.name());
            return runnable;
        } catch (K8sFrameworkException e) {
            log.warn("unable to check resources of runnable {}: {}", runnable.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * True if the main resources of the runnable still exist
     */
    protected abstract boolean exists(T runnable) throws K8sFrameworkException;

    /**
     * Resolve a resource: missing (null or API 404) means not found, any other failure is thrown
     */
    protected static boolean found(Callable<?> get) throws K8sFrameworkException {
        try {
            return get.call() != null;
        } catch (K8sFrameworkException e) {
            if (e.isNotFound()) {
                return false;
            }
            throw e;
        } catch (Exception e) {
            throw new K8sFrameworkException(e.getMessage());
        }
    }

    protected static boolean isDeleting(K8sRunnable runnable) {
        return K8sRunnableState.DELETING.name().equals(runnable.getState());
    }

    private Class<?> lockType() {
        //same key as the listener: the runnable type of the store
        return store.getResolvableType().resolve();
    }

    public abstract T refresh(T runnable);

    protected void publish(T runnable) {
        if (eventPublisher != null) {
            log.debug("publish run {}", runnable.getId());

            // Send message to Serve manager
            eventPublisher.publishEvent(RunnableChangedEvent.build(runnable, null));
        }
    }

    protected String lastLogs(K8sBaseFramework<T, ?> framework, List<V1Pod> pods) {
        if (!Boolean.TRUE.equals(collectLogs)) {
            //disabled
            return null;
        }

        if (pods == null || pods.isEmpty()) {
            return null;
        }

        //try to fetch last log lines to add to message
        try {
            String containerId = pods
                .stream()
                .findFirst()
                .map(p -> p.getStatus())
                .map(s -> s.getContainerStatuses())
                .map(sc -> sc.getFirst())
                .map(cs -> cs.getName())
                .orElse(null);
            if (containerId != null) {
                log.debug("Fetching logs for container {}", containerId);
            }

            log.debug("Fetch last logs from failed pods for container {}...", containerId);
            List<CoreLog> logs = framework.logs(pods);
            return logs
                .stream()
                .filter(l -> l.container().equals(containerId) && l.value() != null && !l.value().isEmpty())
                .findFirst()
                .map(l -> {
                    String content = l.value();
                    //get last few lines of the log
                    String[] lines = content.split("\n");
                    int lastLines = Math.min(lines.length, 8);
                    String lastLog = String.join(
                        "\n",
                        Arrays.copyOfRange(lines, lines.length - lastLines, lines.length)
                    );
                    String message = String.format(
                        "Last logs for container %s: \n------------------------ \n%s",
                        containerId,
                        lastLog
                    );
                    return message;
                })
                .orElse(null);
        } catch (Exception e) {
            log.error("Failed to fetch last logs for pods: {}", e.getMessage(), e);
            return null;
        }
    }
}
