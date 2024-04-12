package it.smartcommunitylabdhub.framework.kaniko.old.builders;

import it.smartcommunitylabdhub.commons.infrastructure.Builder;
import it.smartcommunitylabdhub.framework.kaniko.old.specs.function.FunctionKanikoSpec;
import it.smartcommunitylabdhub.framework.kaniko.old.specs.run.RunKanikoSpec;
import it.smartcommunitylabdhub.framework.kaniko.old.specs.task.TaskBuildPythonSpec;
import java.util.Optional;

/**
 * KanikoBuildBuilder
 * <p>
 * You can use this as a simple class or as a registered bean. If you want to retrieve this as bean from BuilderFactory
 * you have to register it using the following annotation:
 *
 * @BuilderComponent(runtime = "dbt", task = "transform")
 */

public class KanikoBuildPythonBuilder implements Builder<FunctionKanikoSpec, TaskBuildPythonSpec, RunKanikoSpec> {

    @Override
    public RunKanikoSpec build(FunctionKanikoSpec funSpec, TaskBuildPythonSpec taskSpec, RunKanikoSpec runSpec) {
        RunKanikoSpec runKanikoSpec = new RunKanikoSpec(runSpec.toMap());
        runKanikoSpec.setTaskBuildPythonSpec(taskSpec);
        runKanikoSpec.setFuncSpec(funSpec);

        //let run override k8s specs
        Optional
                .ofNullable(runSpec.getTaskBuildPythonSpec())
                .ifPresent(k8sSpec -> runKanikoSpec.getTaskBuildJavaSpec().configure(k8sSpec.toMap()));

        return runKanikoSpec;
    }
}
