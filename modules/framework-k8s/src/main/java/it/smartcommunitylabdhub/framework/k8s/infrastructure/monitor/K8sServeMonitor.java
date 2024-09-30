package it.smartcommunitylabdhub.framework.k8s.infrastructure.monitor;

import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.MonitorComponent;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sDeploymentFramework;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sServeFramework;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreMetric;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sServeRunnable;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Slf4j
@ConditionalOnKubernetes
@Component
@MonitorComponent(framework = K8sServeFramework.FRAMEWORK)
public class K8sServeMonitor extends K8sBaseMonitor<K8sServeRunnable> {

    private final K8sServeFramework serveFramework;
    // TODO remove
    private final K8sDeploymentFramework deploymentFramework;

    public K8sServeMonitor(
            RunnableStore<K8sServeRunnable> runnableStore,
            K8sServeFramework serveFramework,
            K8sDeploymentFramework deploymentFramework) {
        super(runnableStore);
        Assert.notNull(deploymentFramework, "deployment framework is required");
        Assert.notNull(serveFramework, "serve framework is required");

        this.serveFramework = serveFramework;
        this.deploymentFramework = deploymentFramework;
    }

    @Override
    public K8sServeRunnable refresh(K8sServeRunnable runnable) {
        try {
            V1Deployment deployment = deploymentFramework.get(serveFramework.buildDeployment(runnable));
            V1Service service = serveFramework.get(serveFramework.build(runnable));

            // check status
            // if ERROR signal, otherwise let RUNNING
            if (deployment == null || service == null || deployment.getStatus() == null
                    || service.getStatus() == null) {
                // something is missing, no recovery
                log.error("Missing or invalid deployment for {}", runnable.getId());
                runnable.setState(State.ERROR.name());
            }

            log.debug("deployment status: replicas {}", deployment.getStatus().getReadyReplicas());
            log.debug("service status: {}", service.getStatus());
            if (log.isTraceEnabled()) {
                log.trace("deployment status: {}", deployment.getStatus().toString());
            }

            // try to fetch pods
            List<V1Pod> pods = null;
            try {
                log.debug(
                        "Collect pods for deployment {} for run {}",
                        deployment.getMetadata().getName(),
                        runnable.getId());
                pods = deploymentFramework.pods(deployment);
            } catch (K8sFrameworkException e1) {
                log.error("error collecting pods for deployment {}: {}", runnable.getId(), e1.getMessage());
            }

            if (!"disable".equals(collectResults)) {
                // update results
                try {
                    runnable.setResults(
                            MapUtils.mergeMultipleMaps(
                                    runnable.getResults(),
                                    Map.of(
                                            "deployment",
                                            mapper.convertValue(deployment, typeRef),
                                            "service",
                                            mapper.convertValue(service, typeRef),
                                            "pods",
                                            pods != null ? mapper.convertValue(pods, arrayRef) : null)));
                } catch (IllegalArgumentException e) {
                    log.error("error reading k8s results: {}", e.getMessage());
                }
            }

            if (Boolean.TRUE.equals(collectLogs)) {
                // collect logs, optional
                try {
                    log.debug(
                            "Collect logs for deployment {} for run {}",
                            deployment.getMetadata().getName(),
                            runnable.getId());
                    // TODO add sinceTime when available
                    runnable.setLogs(deploymentFramework.logs(deployment));
                } catch (K8sFrameworkException e1) {
                    log.error("error collecting logs for {}: {}", runnable.getId(), e1.getMessage());
                }
            }

            if (Boolean.TRUE.equals(collectMetrics)) {
                // collect metrics, optional
                try {
                    log.debug(
                            "Collect metrics for deployment {} for run {}",
                            deployment.getMetadata().getName(),
                            runnable.getId());

                    List<CoreMetric> coreMetrics = deploymentFramework.metrics(deployment);

                    // TODO fix this, mix with stats from proxy
                    coreMetrics.addAll(serveFramework.stats(service));

                    // Merge metrics and stats.from proxy
                    runnable.setMetrics(
                            coreMetrics);
                } catch (K8sFrameworkException e1) {
                    log.error("error collecting metrics for {}: {}", runnable.getId(), e1.getMessage());
                }
            }
        } catch (K8sFrameworkException e) {
            // Set Runnable to ERROR state
            runnable.setState(State.ERROR.name());
        }

        return runnable;
    }
}
