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
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.framework.k8s.jackson.KubernetesMapper;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.RunnableEventPublisher;
import it.smartcommunitylabdhub.runtimes.events.RunnableChangedEvent;
import it.smartcommunitylabdhub.runtimes.store.RunnableStore;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    protected Boolean collectLogs = Boolean.TRUE;
    protected Boolean collectMetrics = Boolean.TRUE;
    protected String collectResults = "default";

    protected K8sBaseMonitor(RunnableStore<T> runnableStore) {
        Assert.notNull(runnableStore, "runnable store is required");

        this.store = runnableStore;
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

        //fetch all active runnables and THEN update to detach from repository and avoid lazy loading issues
        List<T> runnables = store
            .findAll()
            .stream()
            .filter(runnable -> runnable.getState() != null && !runnable.isTransient())
            .toList();

        runnables
            .stream()
            .forEach(runnable -> {
                log.debug("monitor run {}", runnable.getId());

                if (log.isTraceEnabled()) {
                    log.trace("runnable: {}", runnable);
                }

                //if final avoid refresh
                T refreshed = runnable.isFinal() ? runnable : refresh(runnable);
                if (log.isTraceEnabled()) {
                    log.trace("refreshed: {}", refreshed);
                }

                // Update the runnable
                try {
                    //if runnable state is final, remove from store, otherwise update
                    if (refreshed.isFinal()) {
                        log.debug("delete run {} with state {}", refreshed.getId(), refreshed.getState());
                        store.remove(refreshed.getId());
                    } else {
                        log.debug("store run {} with state {}", refreshed.getId(), refreshed.getState());
                        store.store(refreshed.getId(), refreshed);
                    }

                    //always publish, even if final. We expect receivers to be idempotent
                    publish(refreshed);
                } catch (StoreException e) {
                    log.error("Error with runnable store: {}", e.getMessage());
                }
            });

        log.debug("monitor completed.");
    }

    public void monitor(String id) throws StoreException {
        try {
            T runnable = store.find(id);
            if (runnable == null) {
                //nothing to do
                log.debug("runnable {} not found", id);
                return;
            }

            //skip refresh for transient states, we don't wanna override an operation in progress
            if (runnable.getState() != null && runnable.isTransient()) {
                log.debug("runnable {} in transient state {}, skipping refresh", id, runnable.getState());
                return;
            }

            //if final avoid refresh
            T refreshed = runnable.isFinal() ? runnable : refresh(runnable);
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

            //always publish, even if final. We expect receivers to be idempotent
            publish(refreshed);
        } catch (StoreException e) {
            log.error("Error with runnable store: {}", e.getMessage());
            throw e;
        }
    }

    public abstract T refresh(T runnable);

    protected void publish(T runnable) {
        if (eventPublisher != null) {
            log.debug("publish run {}", runnable.getId());

            // Send message to Serve manager
            eventPublisher.publishEvent(RunnableChangedEvent.build(runnable, null));
        }
    }
}
