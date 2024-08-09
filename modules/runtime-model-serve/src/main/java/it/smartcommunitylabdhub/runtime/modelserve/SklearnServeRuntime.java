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
import it.smartcommunitylabdhub.runtime.modelserve.runners.SklearnServeRunner;
import it.smartcommunitylabdhub.runtime.modelserve.specs.ModelServeRunStatus;
import it.smartcommunitylabdhub.runtime.modelserve.specs.SklearnServeTaskSpec;
import it.smartcommunitylabdhub.runtime.modelserve.specs.SklearnServeFunctionSpec;
import it.smartcommunitylabdhub.runtime.modelserve.specs.SklearnServeRunSpec;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
@RuntimeComponent(runtime = SklearnServeRuntime.RUNTIME)
public class SklearnServeRuntime extends K8sBaseRuntime<SklearnServeFunctionSpec, SklearnServeRunSpec, ModelServeRunStatus, K8sRunnable> {


    public static final String RUNTIME = "sklearnserve";

    @Autowired
    private SecretService secretService;

    @Value("${runtime.sklearnserve.image}")
    private String image;

    public SklearnServeRuntime() {
        super(SklearnServeRunSpec.KIND);
    }

    @Override
    public SklearnServeRunSpec build(@NotNull Executable function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!SklearnServeRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), SklearnServeRunSpec.KIND)
            );
        }

        SklearnServeFunctionSpec funSpec = SklearnServeFunctionSpec.with(function.getSpec());
        SklearnServeRunSpec runSpec = SklearnServeRunSpec.with(run.getSpec());

        String kind = task.getKind();

        //build task spec as defined
        TaskBaseSpec taskSpec =
            switch (kind) {
                case SklearnServeTaskSpec.KIND -> {
                    yield SklearnServeTaskSpec.with(task.getSpec());
                }
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build run merging task spec overrides
        Map<String, Serializable> map = new HashMap<>();
        map.putAll(runSpec.toMap());
        taskSpec.toMap().forEach(map::putIfAbsent);

        SklearnServeRunSpec serveSpec = SklearnServeRunSpec.with(map);
        //ensure function is not modified
        serveSpec.setFunctionSpec(funSpec);

        return serveSpec;
    }

    @Override
    public K8sRunnable run(@NotNull Run run) {
        //check run kind
        if (!SklearnServeRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), SklearnServeRunSpec.KIND)
            );
        }

        SklearnServeRunSpec runSpec = SklearnServeRunSpec.with(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunUtils.parseTask(runSpec.getTask());

        return switch (runAccessor.getTask()) {
            case SklearnServeTaskSpec.KIND -> new SklearnServeRunner(
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
