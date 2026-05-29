/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.argo.listeners;

import it.smartcommunitylabdhub.framework.argo.infrastructure.k8s.K8sArgoWorkflowFramework;
import it.smartcommunitylabdhub.framework.argo.runnables.K8sArgoWorkflowRunnable;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.listeners.K8sRunnableListener;
import it.smartcommunitylabdhub.runtimes.events.RunnableListener;
import it.smartcommunitylabdhub.runtimes.store.RunnableStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnKubernetes
@Slf4j
public class K8sArgoWorkflowRunnableListener
    extends K8sRunnableListener<K8sArgoWorkflowRunnable>
    implements RunnableListener<K8sArgoWorkflowRunnable>
{

    public K8sArgoWorkflowRunnableListener(
        K8sArgoWorkflowFramework k8sFramework,
        RunnableStore<K8sArgoWorkflowRunnable> runnableStore
    ) {
        super(k8sFramework, runnableStore);
    }

    public void listen(K8sArgoWorkflowRunnable runnable) {
        if (runnable != null) {
            process(runnable);
        }
    }
}
