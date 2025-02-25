package it.smartcommunitylabdhub.framework.argo.infrastructure.monitor;

import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1NodeStatus;
import io.kubernetes.client.openapi.models.V1Pod;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.MonitorComponent;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.framework.argo.infrastructure.k8s.K8sArgoWorkflowFramework;
import it.smartcommunitylabdhub.framework.argo.objects.K8sWorkflowObject;
import it.smartcommunitylabdhub.framework.argo.runnables.K8sArgoWorkflowRunnable;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.monitor.K8sBaseMonitor;


import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Slf4j
@ConditionalOnKubernetes
@Component
@MonitorComponent(framework = "build")
public class K8sArgoWorkflowMonitor extends K8sBaseMonitor<K8sArgoWorkflowRunnable> {

    private static final String LABEL_PREFIX = "kfp-digitalhub-runtime-";

    private final K8sArgoWorkflowFramework framework;

    public K8sArgoWorkflowMonitor(
            RunnableStore<K8sArgoWorkflowRunnable> runnableStore,
            K8sArgoWorkflowFramework argoFramework
    ) {
        super(runnableStore);
        Assert.notNull(argoFramework, "argo framework is required");

        this.framework = argoFramework;
    }

    @Override
    public K8sArgoWorkflowRunnable refresh(K8sArgoWorkflowRunnable runnable) {
        try {
            K8sWorkflowObject workflow = framework.get(framework.build(runnable));

            if (workflow == null || workflow.getWorkflow().getStatus() == null) {
                // something is missing, no recovery
                log.error("Missing or invalid Argo Workflow for {}", runnable.getId());
                runnable.setState(State.ERROR.name());
                runnable.setError("Argo Workflow missing or invalid");
            }

            log.info("Argo Workflow status: {}", workflow.getWorkflow().getStatus().toString());

            //target for succeded/failed is 1
            String phase = workflow.getWorkflow().getStatus().getPhase();
            if (phase != null && "Succeeded".equals(phase)) {
                // Job has succeeded
                runnable.setState(State.COMPLETED.name());
            } else if (phase != null && ("Failed".equals(phase) || "Error".equals(phase))) {
                // Job has failed delete job and pod
                runnable.setState(State.ERROR.name());
                runnable.setError("Job failed: " + workflow.getWorkflow().getStatus().getMessage());
            }

            //try to fetch pods
            List<V1Pod> pods = null;
            try {
                pods = framework.pods(workflow);
            } catch (K8sFrameworkException e1) {
                log.error("error collecting pods for job {}: {}", runnable.getId(), e1.getMessage());
            }


            //update results
            try {
                // Collect nodes from workflow
                Map<String, IoArgoprojWorkflowV1alpha1NodeStatus> nodes =
                        workflow.getWorkflow().getStatus().getNodes();

                runnable.setResults(
                        Stream.of(new AbstractMap.SimpleEntry<>(
                                                "workflow", mapper.convertValue(workflow, typeRef)),
                                        new AbstractMap.SimpleEntry<>(
                                                "pods",
                                                pods != null ? mapper.convertValue(pods, arrayRef) : null),
                                        new AbstractMap.SimpleEntry<>(
                                                "nodes",
                                                nodes != null ? mapper.convertValue(nodes, typeRef) : null)
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