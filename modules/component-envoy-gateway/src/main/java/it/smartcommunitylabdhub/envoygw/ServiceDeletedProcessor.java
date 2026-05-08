package it.smartcommunitylabdhub.envoygw;

import it.smartcommunitylabdhub.commons.annotations.common.ProcessorType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.models.status.Status;
import it.smartcommunitylabdhub.envoygw.specs.GatewayRunStatus;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sCRFramework;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runs.specs.RunBaseStatus;
import java.io.Serializable;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@ProcessorType(stages = { "onDeleted" }, type = Run.class, spec = Status.class)
@Component
@Slf4j
public class ServiceDeletedProcessor implements Processor<Run, RunBaseStatus> {

    private final K8sCRFramework k8sCRFramework;

    public ServiceDeletedProcessor(K8sCRFramework k8sCRFramework) {
        this.k8sCRFramework = k8sCRFramework;
    }

    @Override
    public <I> RunBaseStatus process(String stage, Run run, I input) throws CoreRuntimeException {
        try {
            //read event

            Map<String, Serializable> status = run.getStatus();

            // check run has service info and has not been populated, otherwise ignore
            if (status != null && status.get("service") != null && status.get("gatewayInfo") != null) {
                GatewayRunStatus gatewayRunStatus = GatewayRunStatus.with(status);

                if (gatewayRunStatus.getRunnables() != null) {                    
                    // delete in reverse order to avoid dependencies issues
                    for (int i = gatewayRunStatus.getRunnables().size() - 1; i >= 0; i--) {
                        K8sCRRunnable crRunnable = gatewayRunStatus.getRunnables().get(i);
                        k8sCRFramework.delete(crRunnable);
                    }
                }
                gatewayRunStatus.setRunnables(null);
                return gatewayRunStatus;
            }
        } catch (Exception e) {
            log.error("Error handling runnable changed event: {}", e.getMessage(), e);
        }
        return null;
    }
}
