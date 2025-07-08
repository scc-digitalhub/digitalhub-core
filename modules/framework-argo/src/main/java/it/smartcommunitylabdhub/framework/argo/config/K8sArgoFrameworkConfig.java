/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.argo.config;

import io.kubernetes.client.openapi.ApiClient;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.framework.argo.infrastructure.k8s.K8sArgoCronWorkflowFramework;
import it.smartcommunitylabdhub.framework.argo.infrastructure.k8s.K8sArgoWorkflowFramework;
import it.smartcommunitylabdhub.framework.argo.runnables.K8sArgoCronWorkflowRunnable;
import it.smartcommunitylabdhub.framework.argo.runnables.K8sArgoWorkflowRunnable;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class K8sArgoFrameworkConfig {

    @Bean
    @ConditionalOnKubernetes
    public RunnableStore<K8sArgoWorkflowRunnable> k8sArgoRunnableStoreService(
        RunnableStore.StoreSupplier storeSupplier
    ) {
        return storeSupplier.get(K8sArgoWorkflowRunnable.class);
    }

    @Bean
    @ConditionalOnKubernetes
    public RunnableStore<K8sArgoCronWorkflowRunnable> k8sArgoCronRunnableStoreService(
        RunnableStore.StoreSupplier storeSupplier
    ) {
        return storeSupplier.get(K8sArgoCronWorkflowRunnable.class);
    }

    @Bean
    @ConditionalOnKubernetes
    public K8sArgoWorkflowFramework k8sArgoWorkflowFramework(ApiClient apiClient) {
        return new K8sArgoWorkflowFramework(apiClient);
    }

    @Bean
    @ConditionalOnKubernetes
    public K8sArgoCronWorkflowFramework k8sArgoCronWorkflowFramework(ApiClient apiClient) {
        return new K8sArgoCronWorkflowFramework(apiClient);
    }
}
