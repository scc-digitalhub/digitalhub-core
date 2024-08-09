package it.smartcommunitylabdhub.runtime.modelserve;

import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.commons.models.base.Executable;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.models.entities.task.Task;
import it.smartcommunitylabdhub.commons.models.entities.task.TaskBaseSpec;
import it.smartcommunitylabdhub.commons.models.utils.RunUtils;
import it.smartcommunitylabdhub.commons.services.entities.SecretService;
import it.smartcommunitylabdhub.framework.k8s.base.K8sBaseRuntime;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runtime.modelserve.runners.MlflowServeRunner;
import it.smartcommunitylabdhub.runtime.modelserve.specs.MlflowServeFunctionSpec;
import it.smartcommunitylabdhub.runtime.modelserve.specs.MlflowServeRunSpec;
import it.smartcommunitylabdhub.runtime.modelserve.specs.MlflowServeTaskSpec;
import it.smartcommunitylabdhub.runtime.modelserve.specs.ModelServeRunStatus;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
@RuntimeComponent(runtime = MlflowServeRuntime.RUNTIME)
public class MlflowServeRuntime extends K8sBaseRuntime<MlflowServeFunctionSpec, MlflowServeRunSpec, ModelServeRunStatus, K8sRunnable> {


    public static final String RUNTIME = "mlflowserve";

    @Autowired
    private SecretService secretService;

    @Value("${runtime.mlflowserve.image}")
    private String image;

    public MlflowServeRuntime() {
        super(MlflowServeRunSpec.KIND);
    }

    @Override
    public MlflowServeRunSpec build(@NotNull Executable function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!MlflowServeRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), MlflowServeRunSpec.KIND)
            );
        }

        MlflowServeFunctionSpec funSpec = MlflowServeFunctionSpec.with(function.getSpec());
        MlflowServeRunSpec runSpec = MlflowServeRunSpec.with(run.getSpec());

        String kind = task.getKind();

        //build task spec as defined
        TaskBaseSpec taskSpec =
            switch (kind) {
                case MlflowServeTaskSpec.KIND -> {
                    yield MlflowServeTaskSpec.with(task.getSpec());
                }
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build run merging task spec overrides
        Map<String, Serializable> map = new HashMap<>();
        map.putAll(runSpec.toMap());
        taskSpec.toMap().forEach(map::putIfAbsent);

        MlflowServeRunSpec serveSpec = MlflowServeRunSpec.with(map);
        //ensure function is not modified
        serveSpec.setFunctionSpec(funSpec);

        return serveSpec;
    }

    @Override
    public K8sRunnable run(@NotNull Run run) {
        //check run kind
        if (!MlflowServeRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), MlflowServeRunSpec.KIND)
            );
        }

        MlflowServeRunSpec runSpec = MlflowServeRunSpec.with(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunUtils.parseTask(runSpec.getTask());

        return switch (runAccessor.getTask()) {
            case MlflowServeTaskSpec.KIND -> new MlflowServeRunner(
                image,
                runSpec.getFunctionSpec(),
                secretService.groupSecrets(run.getProject(), runSpec.getTaskServeSpec().getSecrets()),
                k8sBuilderHelper
            )
            .produce(run);

            default -> throw new IllegalArgumentException("Kind not recognized. Cannot retrieve the right Runner");
        };
    }
}
