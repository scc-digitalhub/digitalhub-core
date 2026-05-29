/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.ray.listeners;

import it.smartcommunitylabdhub.framework.k8s.listeners.K8sRunnableListener;
import it.smartcommunitylabdhub.framework.ray.infrastructure.k8s.K8sRayServiceFramework;
import it.smartcommunitylabdhub.framework.ray.runnables.K8sRayServiceRunnable;
import it.smartcommunitylabdhub.runtimes.store.RunnableStore;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

public class K8sRayServiceListener extends K8sRunnableListener<K8sRayServiceRunnable> {

    public K8sRayServiceListener(
        K8sRayServiceFramework k8sFramework,
        RunnableStore<K8sRayServiceRunnable> runnableStore
    ) {
        super(k8sFramework, runnableStore);
    }

    @Async
    @EventListener
    public void listen(K8sRayServiceRunnable runnable) {
        if (runnable != null) {
            process(runnable);
        }
    }
}
