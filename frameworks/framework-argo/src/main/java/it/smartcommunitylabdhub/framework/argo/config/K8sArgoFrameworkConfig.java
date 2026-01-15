/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.argo.config;

import io.kubernetes.client.openapi.ApiClient;
import it.smartcommunitylabdhub.commons.config.YamlPropertySourceFactory;
import it.smartcommunitylabdhub.framework.argo.infrastructure.k8s.K8sArgoWorkflowFramework;
import it.smartcommunitylabdhub.framework.argo.runnables.K8sArgoWorkflowRunnable;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.runtimes.persistence.RunnableRepository;
import it.smartcommunitylabdhub.runtimes.store.RunnableStore;
import it.smartcommunitylabdhub.runtimes.store.RunnableStoreImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/framework-argo.yml", factory = YamlPropertySourceFactory.class)
public class K8sArgoFrameworkConfig {

    @Bean
    @ConditionalOnKubernetes
    public RunnableStore<K8sArgoWorkflowRunnable> k8sArgoRunnableStoreService(RunnableRepository runnableRepository) {
        return new RunnableStoreImpl<>(K8sArgoWorkflowRunnable.class, runnableRepository);
    }

    @Bean
    @ConditionalOnKubernetes
    public K8sArgoWorkflowFramework k8sArgoWorkflowFramework(ApiClient apiClient) {
        return new K8sArgoWorkflowFramework(apiClient);
    }
}
