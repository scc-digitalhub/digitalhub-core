package it.smartcommunitylabdhub.runtime.kfp.runners;

import it.smartcommunitylabdhub.commons.infrastructure.Runner;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.framework.argo.runnables.K8sArgoCronWorkflowRunnable;
import it.smartcommunitylabdhub.framework.argo.runnables.K8sArgoWorkflowRunnable;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.runtime.kfp.KFPRuntime;
import it.smartcommunitylabdhub.runtime.kfp.specs.KFPPipelineTaskSpec;
import it.smartcommunitylabdhub.runtime.kfp.specs.KFPRunSpec;
import it.smartcommunitylabdhub.runtime.kfp.specs.KFPWorkflowSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.util.StringUtils;

/**
 * KFPPipelineRunner
 * <p>
 * You can use this as a simple class or as a registered bean. If you want to retrieve this as bean from RunnerFactory
 * you have to register it using the following annotation:
 *
 * @RunnerComponent(runtime = "kfp", task = "pipeline")
 */
public class KFPPipelineRunner implements Runner<K8sArgoWorkflowRunnable> {

    @Override
    public K8sArgoWorkflowRunnable produce(Run run) {
        KFPRunSpec runSpec = new KFPRunSpec(run.getSpec());
        KFPPipelineTaskSpec taskSpec = runSpec.getTaskPipelineSpec();
        KFPWorkflowSpec workflowSpec = runSpec.getWorkflowSpec();
        if (workflowSpec == null || workflowSpec.getWorkflow() == null) {
            throw new IllegalArgumentException("workflowSpec is null");
        }

        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(
                new CoreEnv("PROJECT_NAME", run.getProject()),
                new CoreEnv("RUN_ID", run.getId())
            )
        );

        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

        K8sArgoWorkflowRunnable argoRunnable = K8sArgoWorkflowRunnable
            .builder()
            .runtime(KFPRuntime.RUNTIME)
            .task(KFPPipelineTaskSpec.KIND)
            .workflowSpec(workflowSpec.getWorkflow())
            .build();

        if (StringUtils.hasText(taskSpec.getSchedule())) {
            //build a cronJob
            argoRunnable = K8sArgoCronWorkflowRunnable
                .builder()
                .runtime(KFPRuntime.RUNTIME)
                .task(KFPPipelineTaskSpec.KIND)
                .workflowSpec(workflowSpec.getWorkflow())
                .schedule(taskSpec.getSchedule())
                .build();
        }

        argoRunnable.setId(run.getId());
        argoRunnable.setProject(run.getProject());

        return argoRunnable;
    }
}
