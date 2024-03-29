package it.smartcommunitylabdhub.runtime.nefertem.builders;

import it.smartcommunitylabdhub.commons.infrastructure.Builder;
import it.smartcommunitylabdhub.runtime.nefertem.specs.function.FunctionNefertemSpec;
import it.smartcommunitylabdhub.runtime.nefertem.specs.run.RunNefertemSpec;
import it.smartcommunitylabdhub.runtime.nefertem.specs.task.TaskValidateSpec;
import java.util.Optional;

/**
 * NefetermValidateBuilder
 * <p>
 * You can use this as a simple class or as a registered bean. If you want to retrieve this as bean from BuilderFactory
 * you have to register it using the following annotation:
 *
 * @BuilderComponent(runtime = "nefertem", task = "validate")
 */

public class NefertemValidateBuilder implements Builder<FunctionNefertemSpec, TaskValidateSpec, RunNefertemSpec> {

    @Override
    public RunNefertemSpec build(FunctionNefertemSpec funSpec, TaskValidateSpec taskSpec, RunNefertemSpec runSpec) {
        RunNefertemSpec runNefertemSpec = new RunNefertemSpec(runSpec.toMap());
        runNefertemSpec.setFuncSpec(funSpec);
        runNefertemSpec.setTaskValidateSpec(taskSpec);

        //let run override k8s specs
        Optional
            .ofNullable(runSpec.getTaskValidateSpec())
            .ifPresent(k8sSpec -> runNefertemSpec.getTaskValidateSpec().configure(k8sSpec.toMap()));

        // Return a run spec
        return runNefertemSpec;
    }
}
