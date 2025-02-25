package it.smartcommunitylabdhub.framework.k8s.infrastructure.monitor;

import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Pod;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.MonitorComponent;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sDeploymentFramework;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sDeploymentRunnable;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Slf4j
@ConditionalOnKubernetes
@Component
@MonitorComponent(framework = K8sDeploymentFramework.FRAMEWORK)
public class K8sDeploymentMonitor extends K8sBaseMonitor<K8sDeploymentRunnable> {

    private final K8sDeploymentFramework framework;

    public K8sDeploymentMonitor(
        RunnableStore<K8sDeploymentRunnable> runnableStore,
        K8sDeploymentFramework deploymentFramework
    ) {
        super(runnableStore);
        Assert.notNull(deploymentFramework, "deployment framework is required");

        this.framework = deploymentFramework;
    }

    @Override
    public K8sDeploymentRunnable refresh(K8sDeploymentRunnable runnable) {
        try {
            V1Deployment deployment = framework.get(framework.build(runnable));

            // check status
            // if ERROR signal, otherwise let RUNNING
            if (deployment == null || deployment.getStatus() == null) {
                // something is missing, no recovery
                log.error("Missing or invalid deployment for {}", runnable.getId());
                runnable.setState(State.ERROR.name());
                runnable.setError("Deployment missing or invalid");
            }

            log.debug("deployment status: replicas {}", deployment.getStatus().getReadyReplicas());
            if (log.isTraceEnabled()) {
                log.trace("deployment status: {}", deployment.getStatus().toString());
            }

            //try to fetch pods
            List<V1Pod> pods = null;
            try {
                log.debug(
                    "Collect pods for deployment {} for run {}",
                    deployment.getMetadata().getName(),
                    runnable.getId()
                );
                pods = framework.pods(deployment);
            } catch (K8sFrameworkException e1) {
                log.error("error collecting pods for deployment {}: {}", runnable.getId(), e1.getMessage());
            }

            //if number of retry on pods increases, stop
            if (pods != null) {
                boolean hasRestarts = pods
                    .stream()
                    .anyMatch(pod ->
                        Optional
                            .ofNullable(pod.getStatus())
                            .map(status -> {
                                boolean initRestarts = Optional
                                    .ofNullable(status.getInitContainerStatuses())
                                    .map(s -> s.stream().map(i -> i.getRestartCount()).anyMatch(r -> r > 1))
                                    .orElse(false);

                                boolean restarts = Optional
                                    .ofNullable(status.getContainerStatuses())
                                    .map(s -> s.stream().map(i -> i.getRestartCount()).anyMatch(r -> r > 1))
                                    .orElse(false);

                                return initRestarts || restarts;
                            })
                            .orElse(false)
                    );

                // if RESTARTS signal, otherwise let RUNNING
                if (hasRestarts) {
                    // we observed multiple restarts, stop it
                    log.error("Multiple restarts observed {}", runnable.getId());
                    runnable.setState(State.ERROR.name());
                    runnable.setError("Multiple pod restarts");
                }
            }

            if (!"disable".equals(collectResults)) {
                //update results
                try {
                    runnable.setResults(
                            MapUtils.mergeMultipleMaps(
                                    runnable.getResults(),
                                    Stream.of(new AbstractMap.SimpleEntry<>(
                                                            "deployment",
                                                            mapper.convertValue(deployment, typeRef)
                                                    ),
                                                    new AbstractMap.SimpleEntry<>(
                                                            "pods",
                                                            pods != null ? mapper.convertValue(pods, arrayRef) : null)
                                            )
                                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                            )
                    );
                } catch (IllegalArgumentException e) {
                    log.error("error reading k8s results: {}", e.getMessage());
                }
            }

            if (Boolean.TRUE.equals(collectLogs)) {
                //collect logs, optional
                try {
                    log.debug(
                        "Collect logs for deployment {} for run {}",
                        deployment.getMetadata().getName(),
                        runnable.getId()
                    );
                    //TODO add sinceTime when available
                    runnable.setLogs(framework.logs(deployment));
                } catch (K8sFrameworkException e1) {
                    log.error("error collecting logs for {}: {}", runnable.getId(), e1.getMessage());
                }
            }

            if (Boolean.TRUE.equals(collectMetrics)) {
                //collect metrics, optional
                try {
                    log.debug(
                        "Collect metrics for deployment {} for run {}",
                        deployment.getMetadata().getName(),
                        runnable.getId()
                    );
                    runnable.setMetrics(framework.metrics(deployment));
                } catch (K8sFrameworkException e1) {
                    log.error("error collecting metrics for {}: {}", runnable.getId(), e1.getMessage());
                }
            }
        } catch (K8sFrameworkException e) {
            // Set Runnable to ERROR state
            runnable.setState(State.ERROR.name());
            runnable.setError(e.toError());
        }

        return runnable;
    }
}
