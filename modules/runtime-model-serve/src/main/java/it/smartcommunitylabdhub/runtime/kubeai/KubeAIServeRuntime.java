package it.smartcommunitylabdhub.runtime.kubeai;

import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.commons.models.base.Executable;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.models.task.TaskBaseSpec;
import it.smartcommunitylabdhub.commons.services.ModelService;
import it.smartcommunitylabdhub.framework.k8s.base.K8sBaseRuntime;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import it.smartcommunitylabdhub.runtime.kubeai.specs.KubeAIServeFunctionSpec;
import it.smartcommunitylabdhub.runtime.kubeai.specs.KubeAIServeRunSpec;
import it.smartcommunitylabdhub.runtime.kubeai.specs.KubeAIServeRunStatus;
import it.smartcommunitylabdhub.runtime.kubeai.specs.KubeAIServeTaskSpec;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@RuntimeComponent(runtime = KubeAIServeRuntime.RUNTIME)
public class KubeAIServeRuntime
    extends K8sBaseRuntime<KubeAIServeFunctionSpec, KubeAIServeRunSpec, KubeAIServeRunStatus, K8sCRRunnable>
    implements InitializingBean {

    public static final String RUNTIME = "kubeaiserve";

    @Autowired
    private ModelService modelService;

    public KubeAIServeRuntime() {
        super(KubeAIServeRunSpec.KIND);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // TODO
    }

    @Override
    public KubeAIServeRunSpec build(@NotNull Executable function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!KubeAIServeRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(
                        String.valueOf(run.getKind()),
                        KubeAIServeRunSpec.KIND
                    )
            );
        }

        KubeAIServeFunctionSpec funSpec = KubeAIServeFunctionSpec.with(function.getSpec());
        KubeAIServeRunSpec runSpec = KubeAIServeRunSpec.with(run.getSpec());

        String kind = task.getKind();

        //build task spec as defined
        TaskBaseSpec taskSpec =
            switch (kind) {
                case KubeAIServeTaskSpec.KIND -> {
                    yield KubeAIServeTaskSpec.with(task.getSpec());
                }
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build run merging task spec overrides
        Map<String, Serializable> map = new HashMap<>();
        map.putAll(runSpec.toMap());
        taskSpec.toMap().forEach(map::putIfAbsent);

        KubeAIServeRunSpec serveSpec = KubeAIServeRunSpec.with(map);
        //ensure function is not modified
        serveSpec.setFunctionSpec(funSpec);

        return serveSpec;
    }

    @Override
    public K8sCRRunnable run(@NotNull Run run) {
        //check run kind
        if (!KubeAIServeRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(
                        String.valueOf(run.getKind()),
                        KubeAIServeRunSpec.KIND
                    )
            );
        }

        KubeAIServeRunSpec runSpec = KubeAIServeRunSpec.with(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        return switch (runAccessor.getTask()) {
            case KubeAIServeTaskSpec.KIND -> new KubeAIServeRunner(
                runSpec.getFunctionSpec(),
                modelService
            )
                .produce(run);
            default -> throw new IllegalArgumentException("Kind not recognized. Cannot retrieve the right Runner");
        };
    }
}
