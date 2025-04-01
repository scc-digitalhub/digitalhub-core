package it.smartcommunitylabdhub.runtime.kfp.runners;

import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.framework.argo.runnables.K8sArgoCronWorkflowRunnable;
import it.smartcommunitylabdhub.framework.argo.runnables.K8sArgoWorkflowRunnable;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runtime.kfp.KFPRuntime;
import it.smartcommunitylabdhub.runtime.kfp.specs.KFPPipelineTaskSpec;
import it.smartcommunitylabdhub.runtime.kfp.specs.KFPRunSpec;
import it.smartcommunitylabdhub.runtime.kfp.specs.KFPWorkflowSpec;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.util.StringUtils;

public class KFPPipelineRunner {

    public K8sRunnable produce(Run run) {
        KFPRunSpec runSpec = new KFPRunSpec(run.getSpec());
        KFPPipelineTaskSpec taskSpec = runSpec.getTaskPipelineSpec();
        KFPWorkflowSpec workflowSpec = runSpec.getWorkflowSpec();
        if (workflowSpec == null || workflowSpec.getBuild() == null || workflowSpec.getBuild().getBase64() == null) {
            throw new IllegalArgumentException("workflowSpec is null");
        }

        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
        );

        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

        Map<String, Serializable> parameters = new HashMap<>();
        if (runSpec.getParameters() != null) parameters.putAll(runSpec.getParameters());
        if (runSpec.getInputs() != null) parameters.putAll(runSpec.getInputs());

        String argoSpec = new String(
            Base64.getDecoder().decode(workflowSpec.getBuild().getBase64()),
            StandardCharsets.UTF_8
        );

        K8sRunnable argoRunnable = K8sArgoWorkflowRunnable
            .builder()
            .runtime(KFPRuntime.RUNTIME)
            .task(KFPPipelineTaskSpec.KIND)
            .state(State.READY.name())
            .workflowSpec(argoSpec)
            .parameters(parameters)
            .build();

        if (StringUtils.hasText(taskSpec.getSchedule())) {
            //build a cronJob
            argoRunnable =
                K8sArgoCronWorkflowRunnable
                    .builder()
                    .runtime(KFPRuntime.RUNTIME)
                    .state(State.READY.name())
                    .task(KFPPipelineTaskSpec.KIND)
                    .workflowSpec(argoSpec)
                    .parameters(parameters)
                    .schedule(taskSpec.getSchedule())
                    .build();
        }

        argoRunnable.setId(run.getId());
        argoRunnable.setProject(run.getProject());

        return argoRunnable;
    }
}
