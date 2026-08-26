/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.ray.config;

import io.kubernetes.client.openapi.ApiClient;
import it.smartcommunitylabdhub.commons.config.YamlPropertySourceFactory;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.jackson.KubernetesMapper;
import it.smartcommunitylabdhub.framework.ray.infrastructure.k8s.K8sRayJobFramework;
import it.smartcommunitylabdhub.framework.ray.infrastructure.k8s.K8sRayServiceFramework;
import it.smartcommunitylabdhub.framework.ray.listeners.K8sRayJobListener;
import it.smartcommunitylabdhub.framework.ray.listeners.K8sRayServiceListener;
import it.smartcommunitylabdhub.framework.ray.runnables.K8sRayJobRunnable;
import it.smartcommunitylabdhub.framework.ray.runnables.K8sRayServiceRunnable;
import it.smartcommunitylabdhub.runtimes.persistence.RunnableRepository;
import it.smartcommunitylabdhub.runtimes.store.RunnableStore;
import it.smartcommunitylabdhub.runtimes.store.RunnableStoreImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@PropertySource(value = "classpath:/framework-ray.yml", factory = YamlPropertySourceFactory.class)
public class K8sRayFrameworkConfig {

    // ---- runnable stores ----
    @Bean
    @ConditionalOnKubernetes
    public RunnableStore<K8sRayJobRunnable> k8sRayJobRunnableStoreService(
            RunnableRepository runnableRepository,
            PlatformTransactionManager transactionManager) {
        RunnableStoreImpl<K8sRayJobRunnable> store = new RunnableStoreImpl<>(
                K8sRayJobRunnable.class, runnableRepository, transactionManager);
        store.setObjectMapper(KubernetesMapper.CBOR_OBJECT_MAPPER);
        return store;
    }
    @Bean
    @ConditionalOnKubernetes
    public RunnableStore<K8sRayServiceRunnable> k8sRayServiceRunnableStoreService(
            RunnableRepository runnableRepository,
            PlatformTransactionManager transactionManager) {
        RunnableStoreImpl<K8sRayServiceRunnable> store = new RunnableStoreImpl<>(
                K8sRayServiceRunnable.class, runnableRepository, transactionManager);
        store.setObjectMapper(KubernetesMapper.CBOR_OBJECT_MAPPER);
        return store;
    }
    
    // ---- frameworks ----

    @Bean
    @ConditionalOnKubernetes
    public K8sRayJobFramework k8sRayJobFramework(ApiClient apiClient) {
        return new K8sRayJobFramework(apiClient);
    }

    @Bean
    @ConditionalOnKubernetes
    public K8sRayServiceFramework k8sRayServiceFramework(ApiClient apiClient) {
        return new K8sRayServiceFramework(apiClient);
    }

    // ---- listeners ----

    @Bean
    @ConditionalOnKubernetes
    public K8sRayJobListener k8sRayJobListener(
        K8sRayJobFramework framework,
        RunnableStore<K8sRayJobRunnable> store
    ) {
        return new K8sRayJobListener(framework, store);
    }

    @Bean
    @ConditionalOnKubernetes
    public K8sRayServiceListener k8sRayServiceListener(
        K8sRayServiceFramework framework,
        RunnableStore<K8sRayServiceRunnable> store
    ) {
        return new K8sRayServiceListener(framework, store);
    }
}
