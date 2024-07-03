package it.smartcommunitylabdhub.framework.k8s.processors;

import it.smartcommunitylabdhub.commons.annotations.common.RunProcessorType;
import it.smartcommunitylabdhub.commons.infrastructure.RunProcessor;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.models.entities.run.RunBaseStatus;
import it.smartcommunitylabdhub.framework.k8s.model.K8sRunStatus;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import java.io.Serializable;
import java.util.Map;
import org.springframework.stereotype.Component;

@RunProcessorType(stages = { "onRunning", "onCompleted", "onError", "onStopped", "onDeleted" }, id = K8sProcessor.ID)
@Component(K8sProcessor.ID)
public class K8sProcessor implements RunProcessor<RunBaseStatus> {

    public static final String ID = "k8sProcessor";

    @Override
    public RunBaseStatus process(Run run, RunRunnable runRunnable, RunBaseStatus status) {
        if (runRunnable instanceof K8sRunnable) {
            Map<String, Serializable> res = ((K8sRunnable) runRunnable).getResults();
            //extract k8s details
            return K8sRunStatus.builder().k8s(res).build();
        }
        return null;
    }
}
