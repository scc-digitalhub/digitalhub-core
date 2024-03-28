package it.smartcommunitylabdhub.runtime.kaniko.builders;

import it.smartcommunitylabdhub.commons.infrastructure.Builder;
import it.smartcommunitylabdhub.runtime.kaniko.specs.function.FunctionKanikoSpec;
import it.smartcommunitylabdhub.runtime.kaniko.specs.run.RunKanikoSpec;
import it.smartcommunitylabdhub.runtime.kaniko.specs.task.TaskBuildJavaSpec;
import java.util.Optional;

/**
 * KanikoBuildBuilder
 * <p>
 * You can use this as a simple class or as a registered bean. If you want to retrieve this as bean from BuilderFactory
 * you have to register it using the following annotation:
 *
 * @BuilderComponent(runtime = "dbt", task = "transform")
 */

public class KanikoBuildPythonBuilder implements Builder<FunctionKanikoSpec, TaskBuildJavaSpec, RunKanikoSpec> {

    @Override
    public RunKanikoSpec build(FunctionKanikoSpec funSpec, TaskBuildJavaSpec taskSpec, RunKanikoSpec runSpec) {
        RunKanikoSpec runKanikoSpec = new RunKanikoSpec(runSpec.toMap());
        runKanikoSpec.setTaskSpec(taskSpec);
        runKanikoSpec.setFuncSpec(funSpec);

        //let run override k8s specs
        Optional
                .ofNullable(runSpec.getTaskSpec())
                .ifPresent(k8sSpec -> runKanikoSpec.getTaskSpec().configure(k8sSpec.toMap()));

        return runKanikoSpec;
    }
}
