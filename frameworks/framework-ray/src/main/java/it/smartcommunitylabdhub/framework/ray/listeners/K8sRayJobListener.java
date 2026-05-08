/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.ray.listeners;

import it.smartcommunitylabdhub.framework.k8s.listeners.K8sRunnableListener;
import it.smartcommunitylabdhub.framework.ray.infrastructure.k8s.K8sRayJobFramework;
import it.smartcommunitylabdhub.framework.ray.runnables.K8sRayJobRunnable;
import it.smartcommunitylabdhub.runtimes.store.RunnableStore;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

public class K8sRayJobListener extends K8sRunnableListener<K8sRayJobRunnable> {

    public K8sRayJobListener(K8sRayJobFramework k8sFramework, RunnableStore<K8sRayJobRunnable> runnableStore) {
        super(k8sFramework, runnableStore);
    }

    @Async
    @EventListener
    public void listen(K8sRayJobRunnable runnable) {
        if (runnable != null) {
            process(runnable);
        }
    }
}
