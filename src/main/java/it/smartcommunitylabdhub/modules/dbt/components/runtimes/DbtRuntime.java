package it.smartcommunitylabdhub.modules.dbt.components.runtimes;

import it.smartcommunitylabdhub.core.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.core.components.infrastructure.enums.EntityName;
import it.smartcommunitylabdhub.core.components.infrastructure.factories.builders.BuilderFactory;
import it.smartcommunitylabdhub.core.components.infrastructure.factories.runners.RunnerFactory;
import it.smartcommunitylabdhub.core.components.infrastructure.factories.specs.SpecRegistry;
import it.smartcommunitylabdhub.core.components.infrastructure.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.core.components.infrastructure.runtimes.BaseRuntime;
import it.smartcommunitylabdhub.core.exceptions.CoreException;
import it.smartcommunitylabdhub.core.models.accessors.kinds.runs.RunDefaultFieldAccessor;
import it.smartcommunitylabdhub.core.models.accessors.kinds.runs.factories.RunDefaultFieldAccessorFactory;
import it.smartcommunitylabdhub.core.models.accessors.utils.RunAccessor;
import it.smartcommunitylabdhub.core.models.accessors.utils.RunUtils;
import it.smartcommunitylabdhub.core.models.base.RunStatus;
import it.smartcommunitylabdhub.core.models.base.interfaces.Spec;
import it.smartcommunitylabdhub.core.models.entities.run.Run;
import it.smartcommunitylabdhub.core.models.entities.run.specs.RunBaseSpec;
import it.smartcommunitylabdhub.core.models.entities.run.specs.RunRunSpec;
import it.smartcommunitylabdhub.core.models.entities.run.specs.factories.RunRunSpecFactory;
import it.smartcommunitylabdhub.core.models.entities.task.specs.TaskBaseSpec;
import it.smartcommunitylabdhub.core.utils.ErrorList;
import it.smartcommunitylabdhub.core.utils.jackson.JacksonMapper;
import it.smartcommunitylabdhub.modules.dbt.components.builders.DbtTransformBuilder;
import it.smartcommunitylabdhub.modules.dbt.components.runners.DbtTransformRunner;
import it.smartcommunitylabdhub.modules.dbt.models.specs.function.FunctionDbtSpec;
import it.smartcommunitylabdhub.modules.dbt.models.specs.function.factories.FunctionDbtSpecFactory;
import it.smartcommunitylabdhub.modules.dbt.models.specs.run.RunDbtSpec;
import it.smartcommunitylabdhub.modules.dbt.models.specs.task.TaskTransformSpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;

@RuntimeComponent(runtime = DbtRuntime.RUNTIME)
public class DbtRuntime extends BaseRuntime<FunctionDbtSpec, RunDbtSpec, K8sJobRunnable> {

    public static final String RUNTIME = "dbt";

    @Autowired
    SpecRegistry<? extends Spec> specRegistry;

    @Autowired
    FunctionDbtSpecFactory functionDbtSpecFactory;

    @Autowired
    RunDefaultFieldAccessorFactory runDefaultFieldAccessorFactory;

    @Autowired
    RunRunSpecFactory runRunSpecFactory;


    @Value("${runtime.dbt.image}")
    private String image;

    public DbtRuntime(BuilderFactory builderFactory,
                      RunnerFactory runnerFactory) {
        super(builderFactory, runnerFactory);
    }

    @Override
    public RunDbtSpec build(
            FunctionDbtSpec funSpec,
            TaskBaseSpec taskSpec,
            RunBaseSpec runSpec,
            String kind) {

        // Retrieve builder using task kind
        if (kind.equals("transform")) {

            TaskTransformSpec taskTransformSpec = specRegistry.createSpec(
                    "transform",
                    EntityName.TASK,
                    taskSpec.toMap()
            );

            RunRunSpec runRunSpec = specRegistry.createSpec(
                    "run",
                    EntityName.RUN,
                    runSpec.toMap()
            );


            /**
             *  As an alternative, you can use the code below to retrieve the correct builder.
             *  Remember that if you follow this path, you still need to retrieve the SpecRegistry
             *  either with the @Autowired annotation or with the BeanProvider. If you want to retrieve
             *  the builder in this way remember also that you have to register the builder as
             *  a component using the follow annotation: `@BuilderComponent(runtime = "dbt", task = "transform")`
             *  Only by doing this you can get the bean related
             * <p>
             *      DbtTransformBuilder b = getBuilder("transform");
             */

            DbtTransformBuilder builder = new DbtTransformBuilder();

            return builder.build(
                    funSpec,
                    taskTransformSpec,
                    runRunSpec);

        }

        throw new CoreException(
                ErrorList.INTERNAL_SERVER_ERROR.getValue(),
                "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task.",
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }


    @Override
    public K8sJobRunnable run(Run runDTO) {

        /**
         *  As an alternative, you can use the code below to retrieve the correct runner.
         *  Remember that if you follow this path, you still need to retrieve the SpecRegistry
         *  either with the @Autowired annotation or with the BeanProvider. If you want to retrieve
         *  the Runner using specRegistry remember that you have to register also each Runner
         *  component in this way : `@RunnerComponent(runtime = "dbt", task = "transform")`
         *  Only by doing this you can get the bean related
         *
         *      // Retrieve base run spec to use task
         *      RunRunSpec runBaseSpec = specRegistry.createSpec(
         *              runDTO.getKind(),
         *              SpecEntity.RUN,
         *              runDTO.getSpec()
         *      );
         *      RunAccessor runAccessor = RunUtils.parseRun(runBaseSpec.getTask());
         *      Runner runner = getRunner(runAccessor.getTask());
         */

        // Crete spec for run
        RunRunSpec runRunSpec = runRunSpecFactory.create();
        runRunSpec.configure(runDTO.getSpec());

        // Create string run accessor from task
        RunAccessor runAccessor = RunUtils.parseRun(runRunSpec.getTask());

        // Create and configure function dbt spec
        FunctionDbtSpec functionDbtSpec = functionDbtSpecFactory.create();
        functionDbtSpec.configure(runDTO.getSpec());

        // Create and configure default run field accessor
        RunDefaultFieldAccessor runDefaultFieldAccessor = runDefaultFieldAccessorFactory.create();
        runDefaultFieldAccessor.configure(
                JacksonMapper.CUSTOM_OBJECT_MAPPER.convertValue(
                        runDTO,
                        JacksonMapper.typeRef)
        );

        DbtTransformRunner runner = new DbtTransformRunner(
                image,
                runDefaultFieldAccessor);

        return runner.produce(runDTO);
    }


    @Override
    public RunStatus parse() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'parse'");
    }

}
