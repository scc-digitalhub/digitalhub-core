package it.smartcommunitylabdhub.framework.kaniko.old.builders;

import it.smartcommunitylabdhub.framework.kaniko.old.specs.function.FunctionKanikoSpec;
import it.smartcommunitylabdhub.framework.kaniko.old.specs.run.RunKanikoSpec;
import it.smartcommunitylabdhub.framework.kaniko.old.specs.task.TaskBuildJavaSpec;
import java.util.Optional;

/**
 * KanikoBuildBuilder
 * <p>
 * You can use this as a simple class or as a registered bean. If you want to retrieve this as bean from BuilderFactory
 * you have to register it using the following annotation:
 *
 * @BuilderComponent(runtime = "dbt", task = "transform")
 */

public class KanikoBuildJavaBuilder implements Builder<FunctionKanikoSpec, TaskBuildJavaSpec, RunKanikoSpec> {

    @Override
    public RunKanikoSpec build(FunctionKanikoSpec funSpec, TaskBuildJavaSpec taskSpec, RunKanikoSpec runSpec) {
        RunKanikoSpec runKanikoSpec = new RunKanikoSpec(runSpec.toMap());
        runKanikoSpec.setTaskBuildJavaSpec(taskSpec);
        runKanikoSpec.setFuncSpec(funSpec);

        //let run override k8s specs
        Optional
                .ofNullable(runSpec.getTaskBuildJavaSpec())
                .ifPresent(k8sSpec -> runKanikoSpec.getTaskBuildPythonSpec().configure(k8sSpec.toMap()));

        return runKanikoSpec;
    }
}
