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

package it.smartcommunitylabdhub.framework.k8s.infrastructure.watcher;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.monitor.K8sBaseMonitor;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sLabelHelper;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;

@Slf4j
public abstract class K8sBaseWatcher<T extends K8sRunnable> implements InitializingBean {

    protected final ExecutorService executor = Executors.newCachedThreadPool();
    protected final KubernetesClient client;
    protected final K8sBaseMonitor<T> k8sMonitor;

    protected String namespace;
    protected K8sLabelHelper k8sLabelHelper;

    // Debounce map and interval
    // TODO add cleanup for expired entries
    private final Map<String, Long> debounceMap = new ConcurrentHashMap<>();
    private static final long DEBOUNCE_INTERVAL_MS = 100;

    public K8sBaseWatcher(KubernetesClient client, K8sBaseMonitor<T> k8sMonitor) {
        Assert.notNull(k8sMonitor, "k8s monitor is required");
        Assert.notNull(client, "k8s client is required");

        this.client = client;
        this.k8sMonitor = k8sMonitor;
    }

    @Autowired
    public void setK8sLabelHelper(K8sLabelHelper k8sLabelHelper) {
        this.k8sLabelHelper = k8sLabelHelper;
    }

    @Autowired
    public void setNamespace(@Value("${kubernetes.namespace}") String namespace) {
        this.namespace = namespace;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(k8sLabelHelper, "k8s label helper is required");
        Assert.notNull(namespace, "k8s namespace required");
    }

    public abstract void start();

    protected <M extends HasMetadata> Watcher<M> buildWatcher() {
        return new Watcher<M>() {
            @Override
            public void eventReceived(Action action, M resource) {
                if (resource.getMetadata().getLabels() == null) {
                    log.warn("Resource has no labels: {}", resource.getMetadata().getName());
                    return;
                }

                if (action == Action.ADDED) {
                    log.trace("Resource added: {}", resource.getMetadata().getName());

                    //TODO evaluate post added actions?
                    //nothing to monitor for now, framework has already the same version
                    return;
                }

                if (action == Action.DELETED) {
                    log.trace("Resource deleted: {}", resource.getMetadata().getName());

                    //TODO evaluate post delete actions?
                    //nothing to monitor, framework can't collect info anymore
                    return;
                }

                if (action == Action.MODIFIED || action == Action.ERROR) {
                    log.trace("Resource modified: {}", resource.getMetadata().getName());

                    // Get runnable id from pod labels and refresh
                    String runnableId = k8sLabelHelper
                        .extractCoreLabels(resource.getMetadata().getLabels())
                        .getOrDefault("run", null);

                    log.trace(
                        "receive event: [{}] for {} runId {}",
                        action,
                        resource.getMetadata().getName(),
                        String.valueOf(runnableId)
                    );

                    if (runnableId == null) {
                        log.warn("Resource has no runnable id: {}", resource.getMetadata().getName());
                        return;
                    }

                    //trigger refresh
                    debounceAndRefresh(
                        runnableId,
                        () -> {
                            try {
                                // Call the specific refresh method
                                k8sMonitor.monitor(runnableId);
                            } catch (StoreException e) {
                                log.error("Error refreshing: {}", e.getMessage(), e);
                            }
                        }
                    );
                }
            }

            @Override
            public void onClose(WatcherException e) {
                //nothing to do
            }
        };
    }

    //TODO replace simple debounce with debounce+comparison logic to avoid ignoring final states
    protected void debounceAndRefresh(String runnableId, Runnable refreshAction) {
        long now = System.currentTimeMillis();
        debounceMap.compute(
            runnableId,
            (key, lastExecutionTime) -> {
                if (lastExecutionTime == null || (now - lastExecutionTime) > DEBOUNCE_INTERVAL_MS) {
                    refreshAction.run();
                    // Update the last execution time
                    return now;
                }

                return lastExecutionTime;
            }
        );
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down watchers...");
        executor.shutdownNow();
    }
}
