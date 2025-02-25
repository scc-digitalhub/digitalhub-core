package it.smartcommunitylabdhub.framework.argo.infrastructure.monitor;

import io.kubernetes.client.openapi.models.V1Pod;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.MonitorComponent;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.framework.argo.infrastructure.k8s.K8sArgoCronWorkflowFramework;
import it.smartcommunitylabdhub.framework.argo.objects.K8sCronWorkflowObject;
import it.smartcommunitylabdhub.framework.argo.runnables.K8sArgoCronWorkflowRunnable;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.monitor.K8sBaseMonitor;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Slf4j
@ConditionalOnKubernetes
@Component
@MonitorComponent(framework = "build")
public class K8sArgoCronWorkflowMonitor extends K8sBaseMonitor<K8sArgoCronWorkflowRunnable> {

    private final K8sArgoCronWorkflowFramework framework;

    public K8sArgoCronWorkflowMonitor(
        RunnableStore<K8sArgoCronWorkflowRunnable> runnableStore,
        K8sArgoCronWorkflowFramework argoFramework
    ) {
        super(runnableStore);
        Assert.notNull(argoFramework, "argo framework is required");

        this.framework = argoFramework;
    }

    @Override
    public K8sArgoCronWorkflowRunnable refresh(K8sArgoCronWorkflowRunnable runnable) {
        try {
            K8sCronWorkflowObject workflow = framework.get(framework.build(runnable));

            if (workflow == null || workflow.getWorkflow().getStatus() == null) {
                // something is missing, no recovery
                log.error("Missing or invalid Argo Workflow for {}", runnable.getId());
                runnable.setState(State.ERROR.name());
                runnable.setError("Argo Workflow missing or invalid");
            }

            log.info("Argo Workflow status: {}", workflow.getWorkflow().getStatus().toString());

            //try to fetch pods
            List<V1Pod> pods = null;
            try {
                pods = framework.pods(workflow);
            } catch (K8sFrameworkException e1) {
                log.error("error collecting pods for job {}: {}", runnable.getId(), e1.getMessage());
            }

            //update results
            try {
                runnable.setResults(
                        Stream.of(new AbstractMap.SimpleEntry<>("workflow", mapper.convertValue(workflow, typeRef)),
                                        new AbstractMap.SimpleEntry<>(
                                                "pods",
                                                pods != null ? mapper.convertValue(pods, arrayRef) : null)
                                )
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                );
            } catch (IllegalArgumentException e) {
                log.error("error reading k8s results: {}", e.getMessage());
            }

            //collect logs, optional
            try {
                // TODO add sinceTime when available
                // TODO read native argo logs
                runnable.setLogs(framework.logs(workflow));
            } catch (K8sFrameworkException e1) {
                log.error("error collecting logs for Argo Workflow {}: {}", runnable.getId(), e1.getMessage());
            }

            //collect metrics, optional
            try {
                runnable.setMetrics(framework.metrics(workflow));
            } catch (K8sFrameworkException e1) {
                log.error("error collecting metrics for {}: {}", runnable.getId(), e1.getMessage());
            }
        } catch (K8sFrameworkException e) {
            // Set Runnable to ERROR state
            runnable.setState(State.ERROR.name());
            runnable.setError(e.toError());
        }

        return runnable;
    }
}
