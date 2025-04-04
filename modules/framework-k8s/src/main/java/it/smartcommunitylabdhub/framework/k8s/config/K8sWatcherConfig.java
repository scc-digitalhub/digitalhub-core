package it.smartcommunitylabdhub.framework.k8s.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.monitor.K8sDeploymentMonitor;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.monitor.K8sJobMonitor;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.monitor.K8sServeMonitor;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.watcher.K8sDeploymentWatcher;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.watcher.K8sJobWatcher;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.watcher.K8sServeWatcher;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sLabelHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@Slf4j
@Order(7)
@ConditionalOnKubernetes
public class K8sWatcherConfig {

    @Autowired
    private KubernetesClient kubernetesClient;

    @Autowired
    private K8sLabelHelper k8sLabelHelper;

    @Value("${kubernetes.namespace}")
    private String namespace;

    @Bean
    @ConditionalOnKubernetes
    public K8sJobWatcher k8sJobWatcher(K8sJobMonitor k8sJobMonitor) {
        K8sJobWatcher watcher = new K8sJobWatcher(kubernetesClient, k8sJobMonitor);
        watcher.setK8sLabelHelper(k8sLabelHelper);
        watcher.setNamespace(namespace);

        watcher.start();

        return watcher;
    }

    @Bean
    @ConditionalOnKubernetes
    public K8sDeploymentWatcher k8sDeploymentWatcher(K8sDeploymentMonitor k8sDeploymentMonitor) {
        K8sDeploymentWatcher watcher = new K8sDeploymentWatcher(kubernetesClient, k8sDeploymentMonitor);
        watcher.setK8sLabelHelper(k8sLabelHelper);
        watcher.setNamespace(namespace);

        watcher.start();

        return watcher;
    }

    @Bean
    @ConditionalOnKubernetes
    public K8sServeWatcher k8sServeWatcher(K8sServeMonitor k8sServeMonitor) {
        K8sServeWatcher watcher = new K8sServeWatcher(kubernetesClient, k8sServeMonitor);
        watcher.setK8sLabelHelper(k8sLabelHelper);
        watcher.setNamespace(namespace);

        watcher.start();

        return watcher;
    }
}
