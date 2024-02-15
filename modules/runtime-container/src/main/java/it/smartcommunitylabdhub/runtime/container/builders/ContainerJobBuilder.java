package it.smartcommunitylabdhub.runtime.container.builders;

import it.smartcommunitylabdhub.commons.infrastructure.Builder;
import it.smartcommunitylabdhub.framework.k8s.base.K8sTaskBaseSpec;
import it.smartcommunitylabdhub.runtime.container.specs.function.FunctionContainerSpec;
import it.smartcommunitylabdhub.runtime.container.specs.run.RunContainerSpec;
import it.smartcommunitylabdhub.runtime.container.specs.task.TaskJobSpec;

/**
 * ContainerJobBuilder
 * <p>
 * You can use this as a simple class or as a registered bean. If you want to retrieve this as bean from BuilderFactory
 * you have to register it using the following annotation:
 *
 * @BuilderComponent(runtime = "container", task = "job")
 */

public class ContainerJobBuilder implements Builder<FunctionContainerSpec, TaskJobSpec, RunContainerSpec> {

    @Override
    public RunContainerSpec build(FunctionContainerSpec funSpec, TaskJobSpec taskSpec, RunContainerSpec runSpec) {
        RunContainerSpec containerSpec = new RunContainerSpec(runSpec.toMap());
        containerSpec.setTaskJobSpec(taskSpec);
        containerSpec.setFunctionSpec(funSpec);

        //let run override k8s specs
        K8sTaskBaseSpec k8sSpec = runSpec.getTaskJobSpec();
        containerSpec.getTaskJobSpec().configure(k8sSpec.toMap());

        return containerSpec;
    }
}
