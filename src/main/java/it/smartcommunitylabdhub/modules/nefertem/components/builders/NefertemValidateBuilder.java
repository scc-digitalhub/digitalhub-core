package it.smartcommunitylabdhub.modules.nefertem.components.builders;

import it.smartcommunitylabdhub.core.components.infrastructure.factories.builders.Builder;
import it.smartcommunitylabdhub.modules.nefertem.models.specs.function.FunctionNefertemSpec;
import it.smartcommunitylabdhub.modules.nefertem.models.specs.run.RunNefertemSpec;
import it.smartcommunitylabdhub.modules.nefertem.models.specs.task.TaskValidateSpec;

/**
 * NefetermValidateBuilder
 * <p>
 * You can use this as a simple class or as a registered bean. If you want to retrieve this as bean from BuilderFactory
 * you have to register it using the following annotation:
 *
 * @BuilderComponent(runtime = "nefertem", task = "validate")
 */

public class NefertemValidateBuilder implements Builder<
        FunctionNefertemSpec,
        TaskValidateSpec,
        RunNefertemSpec> {

    @Override
    public RunNefertemSpec build(
            FunctionNefertemSpec funSpec,
            TaskValidateSpec taskSpec,
            RunNefertemSpec runSpec) {

        RunNefertemSpec runNefertemSpec =
                RunNefertemSpec.builder()
                        .build();

        runNefertemSpec.configure(runSpec.toMap());
        runNefertemSpec.setFuncSpec(funSpec);
        runNefertemSpec.setTaskValidateSpec(taskSpec);

        // Return a run spec
        return runNefertemSpec;
    }
}
