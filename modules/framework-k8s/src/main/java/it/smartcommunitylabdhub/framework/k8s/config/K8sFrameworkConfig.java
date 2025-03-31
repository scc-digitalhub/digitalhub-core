package it.smartcommunitylabdhub.framework.k8s.config;

import io.kubernetes.client.openapi.ApiClient;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sCRFramework;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sCronJobFramework;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sDeploymentFramework;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sJobFramework;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sServeFramework;
import it.smartcommunitylabdhub.framework.k8s.listeners.K8sCRListener;
import it.smartcommunitylabdhub.framework.k8s.listeners.K8sCronJobListener;
import it.smartcommunitylabdhub.framework.k8s.listeners.K8sDeploymentListener;
import it.smartcommunitylabdhub.framework.k8s.listeners.K8sJobListener;
import it.smartcommunitylabdhub.framework.k8s.listeners.K8sRunnableListener;
import it.smartcommunitylabdhub.framework.k8s.listeners.K8sServeListener;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCronJobRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sDeploymentRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sServeRunnable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class K8sFrameworkConfig {

    @Bean
    @ConditionalOnKubernetes
    @ConfigurationProperties(prefix = "kubernetes")
    public KubernetesProperties kubernetesProperties() {
        return new KubernetesProperties();
    }

    @Bean
    @ConditionalOnKubernetes
    public RunnableStore<K8sServeRunnable> k8sServeRunnableStoreService(RunnableStore.StoreSupplier storeSupplier) {
        return storeSupplier.get(K8sServeRunnable.class);
    }

    @Bean
    @ConditionalOnKubernetes
    public RunnableStore<K8sDeploymentRunnable> k8sDeploymentRunnableStoreService(
        RunnableStore.StoreSupplier storeSupplier
    ) {
        return storeSupplier.get(K8sDeploymentRunnable.class);
    }

    @Bean
    @ConditionalOnKubernetes
    public RunnableStore<K8sJobRunnable> k8sjobRunnableStoreService(RunnableStore.StoreSupplier storeSupplier) {
        return storeSupplier.get(K8sJobRunnable.class);
    }

    @Bean
    @ConditionalOnKubernetes
    public RunnableStore<K8sCronJobRunnable> k8scronJobRunnableStoreService(RunnableStore.StoreSupplier storeSupplier) {
        return storeSupplier.get(K8sCronJobRunnable.class);
    }

    @Bean
    @ConditionalOnKubernetes
    public RunnableStore<K8sCRRunnable> k8sCRRunnableStoreService(
        RunnableStore.StoreSupplier storeSupplier
    ) {
        return storeSupplier.get(K8sCRRunnable.class);
    }

    @Bean
    @ConditionalOnKubernetes
    public K8sJobFramework k8sJobFramework(ApiClient apiClient) {
        return new K8sJobFramework(apiClient);
    }

    @Bean
    @ConditionalOnKubernetes
    public K8sCronJobFramework k8sCronJobFramework(ApiClient apiClient) {
        return new K8sCronJobFramework(apiClient);
    }

    @Bean
    @ConditionalOnKubernetes
    public K8sDeploymentFramework k8sDeploymentFramework(ApiClient apiClient) {
        return new K8sDeploymentFramework(apiClient);
    }

    @Bean
    @ConditionalOnKubernetes
    public K8sServeFramework k8sServeFramework(ApiClient apiClient) {
        return new K8sServeFramework(apiClient);
    }

    @Bean
    @ConditionalOnKubernetes
    public K8sCRFramework k8sCRFramework(ApiClient apiClient) {
        return new K8sCRFramework(apiClient);
    }


    @Bean
    @ConditionalOnKubernetes
    public K8sRunnableListener<K8sJobRunnable> k8sJobRunnableListener(
        K8sJobFramework jobFramework,
        RunnableStore<K8sJobRunnable> store
    ) {
        return new K8sJobListener(jobFramework, store);
    }

    @Bean
    @ConditionalOnKubernetes
    public K8sRunnableListener<K8sCronJobRunnable> k8sCronJobRunnableListener(
        K8sCronJobFramework cronJobFramework,
        RunnableStore<K8sCronJobRunnable> store
    ) {
        return new K8sCronJobListener(cronJobFramework, store);
    }

    @Bean
    @ConditionalOnKubernetes
    public K8sRunnableListener<K8sDeploymentRunnable> k8sDeploymentRunnableListener(
        K8sDeploymentFramework deploymentFramework,
        RunnableStore<K8sDeploymentRunnable> store
    ) {
        return new K8sDeploymentListener(deploymentFramework, store);
    }

    @Bean
    @ConditionalOnKubernetes
    public K8sRunnableListener<K8sCRRunnable> k8sCRRunnableListener(
        K8sCRFramework crFramework,
        RunnableStore<K8sCRRunnable> store
    ) {
        return new K8sCRListener(crFramework, store);
    }

    @Bean
    @ConditionalOnKubernetes
    public K8sRunnableListener<K8sServeRunnable> k8sServeRunnableListener(
        K8sServeFramework serveFramework,
        RunnableStore<K8sServeRunnable> store
    ) {
        return new K8sServeListener(serveFramework, store);
    }
}
