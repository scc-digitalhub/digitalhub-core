package it.smartcommunitylabdhub.framework.k8s.config;

import io.kubernetes.client.openapi.ApiClient;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sCronJobFramework;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sDeploymentFramework;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sJobFramework;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sServeFramework;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCronJobRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sDeploymentRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sServeRunnable;
import it.smartcommunitylabdhub.framework.kaniko.infrastructure.kaniko.K8sKanikoFramework;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sKanikoRunnable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class K8sFrameworkConfig {

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
    public RunnableStore<K8sKanikoRunnable> k8sbuildRunnableStoreService(RunnableStore.StoreSupplier storeSupplier) {
        return storeSupplier.get(K8sKanikoRunnable.class);
    }


    @Bean
    @ConditionalOnKubernetes
    public RunnableStore<K8sCronJobRunnable> k8scronJobRunnableStoreService(RunnableStore.StoreSupplier storeSupplier) {
        return storeSupplier.get(K8sCronJobRunnable.class);
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
    public K8sKanikoFramework k8sBuildFramework(ApiClient apiClient) {
        return new K8sKanikoFramework(apiClient);
    }
}
