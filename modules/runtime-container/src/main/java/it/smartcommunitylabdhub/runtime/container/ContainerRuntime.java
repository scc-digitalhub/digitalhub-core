package it.smartcommunitylabdhub.runtime.container;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.infrastructure.Runtime;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.base.Executable;
import it.smartcommunitylabdhub.commons.models.entities.function.Function;
import it.smartcommunitylabdhub.commons.models.entities.log.Log;
import it.smartcommunitylabdhub.commons.models.entities.log.LogSpec;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.models.entities.task.Task;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.utils.RunUtils;
import it.smartcommunitylabdhub.commons.services.LogService;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.commons.services.entities.FunctionService;
import it.smartcommunitylabdhub.commons.services.entities.SecretService;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sDeploymentFramework;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sJobFramework;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sServeFramework;
import it.smartcommunitylabdhub.framework.k8s.model.K8sLogStatus;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLog;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreMetric;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sDeploymentRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sServeRunnable;
import it.smartcommunitylabdhub.framework.kaniko.infrastructure.k8s.K8sKanikoFramework;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sKanikoRunnable;
import it.smartcommunitylabdhub.runtime.container.builders.ContainerBuildBuilder;
import it.smartcommunitylabdhub.runtime.container.builders.ContainerDeployBuilder;
import it.smartcommunitylabdhub.runtime.container.builders.ContainerJobBuilder;
import it.smartcommunitylabdhub.runtime.container.builders.ContainerServeBuilder;
import it.smartcommunitylabdhub.runtime.container.runners.ContainerBuildRunner;
import it.smartcommunitylabdhub.runtime.container.runners.ContainerDeployRunner;
import it.smartcommunitylabdhub.runtime.container.runners.ContainerJobRunner;
import it.smartcommunitylabdhub.runtime.container.runners.ContainerServeRunner;
import it.smartcommunitylabdhub.runtime.container.specs.function.FunctionContainerSpec;
import it.smartcommunitylabdhub.runtime.container.specs.run.RunContainerSpec;
import it.smartcommunitylabdhub.runtime.container.specs.task.TaskBuildSpec;
import it.smartcommunitylabdhub.runtime.container.specs.task.TaskDeploySpec;
import it.smartcommunitylabdhub.runtime.container.specs.task.TaskJobSpec;
import it.smartcommunitylabdhub.runtime.container.specs.task.TaskServeSpec;
import it.smartcommunitylabdhub.runtime.container.status.RunContainerStatus;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;

@Slf4j
@RuntimeComponent(runtime = ContainerRuntime.RUNTIME)
public class ContainerRuntime
    implements Runtime<FunctionContainerSpec, RunContainerSpec, RunContainerStatus, RunRunnable> {

    private static final ObjectMapper mapper = JacksonMapper.CUSTOM_OBJECT_MAPPER;
    public static final String RUNTIME = "container";

    //TODO make configurable
    public static final int MAX_METRICS = 300;

    private final ContainerJobBuilder jobBuilder = new ContainerJobBuilder();
    private final ContainerDeployBuilder deployBuilder = new ContainerDeployBuilder();
    private final ContainerServeBuilder serveBuilder = new ContainerServeBuilder();
    private final ContainerBuildBuilder buildBuilder = new ContainerBuildBuilder();

    @Autowired
    private SecretService secretService;

    @Autowired(required = false)
    private RunnableStore<K8sServeRunnable> serveRunnableStore;

    @Autowired(required = false)
    private RunnableStore<K8sDeploymentRunnable> deployRunnableStore;

    @Autowired(required = false)
    private RunnableStore<K8sJobRunnable> jobRunnableStore;

    @Autowired(required = false)
    private RunnableStore<K8sKanikoRunnable> buildRunnableStore;

    @Autowired
    private FunctionService functionService;

    @Autowired
    private LogService logService;

    @Override
    public RunContainerSpec build(@NotNull Executable function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!RunContainerSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), RunContainerSpec.KIND)
            );
        }

        FunctionContainerSpec funSpec = new FunctionContainerSpec(function.getSpec());
        RunContainerSpec runSpec = new RunContainerSpec(run.getSpec());

        String kind = task.getKind();

        // Retrieve builder using task kind
        switch (kind) {
            case TaskDeploySpec.KIND -> {
                TaskDeploySpec taskDeploySpec = new TaskDeploySpec(task.getSpec());
                return deployBuilder.build(funSpec, taskDeploySpec, runSpec);
            }
            case TaskJobSpec.KIND -> {
                TaskJobSpec taskJobSpec = new TaskJobSpec(task.getSpec());
                return jobBuilder.build(funSpec, taskJobSpec, runSpec);
            }
            case TaskServeSpec.KIND -> {
                TaskServeSpec taskServeSpec = new TaskServeSpec(task.getSpec());
                return serveBuilder.build(funSpec, taskServeSpec, runSpec);
            }
            case TaskBuildSpec.KIND -> {
                TaskBuildSpec taskBuildSpec = new TaskBuildSpec(task.getSpec());
                return buildBuilder.build(funSpec, taskBuildSpec, runSpec);
            }
            default -> throw new IllegalArgumentException(
                "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
            );
        }
    }

    @Override
    public RunRunnable run(@NotNull Run run) {
        //check run kind
        if (!RunContainerSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), RunContainerSpec.KIND)
            );
        }

        RunContainerSpec runContainerSpec = new RunContainerSpec(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunUtils.parseTask(runContainerSpec.getTask());

        return switch (runAccessor.getTask()) {
            case TaskDeploySpec.KIND -> new ContainerDeployRunner(
                runContainerSpec.getFunctionSpec(),
                secretService.groupSecrets(run.getProject(), runContainerSpec.getTaskDeploySpec().getSecrets())
            )
                .produce(run);
            case TaskJobSpec.KIND -> new ContainerJobRunner(
                runContainerSpec.getFunctionSpec(),
                secretService.groupSecrets(run.getProject(), runContainerSpec.getTaskJobSpec().getSecrets())
            )
                .produce(run);
            case TaskServeSpec.KIND -> new ContainerServeRunner(
                runContainerSpec.getFunctionSpec(),
                secretService.groupSecrets(run.getProject(), runContainerSpec.getTaskServeSpec().getSecrets())
            )
                .produce(run);
            case TaskBuildSpec.KIND -> new ContainerBuildRunner(
                runContainerSpec.getFunctionSpec(),
                secretService.groupSecrets(run.getProject(), runContainerSpec.getTaskBuildSpec().getSecrets())
            )
                .produce(run);
            default -> throw new IllegalArgumentException("Kind not recognized. Cannot retrieve the right Runner");
        };
    }

    @Override
    public RunRunnable stop(Run run) throws NoSuchEntityException {
        //check run kind
        if (!RunContainerSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), RunContainerSpec.KIND)
            );
        }

        RunContainerSpec runContainerSpec = new RunContainerSpec(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunUtils.parseTask(runContainerSpec.getTask());

        try {
            return switch (runAccessor.getTask()) {
                case TaskDeploySpec.KIND -> {
                    if (deployRunnableStore != null) {
                        K8sDeploymentRunnable k8sDeploymentRunnable = deployRunnableStore.find(run.getId());
                        if (k8sDeploymentRunnable == null) {
                            throw new NoSuchEntityException("Deployment not found");
                        }
                        k8sDeploymentRunnable.setState(State.STOP.name());
                        yield k8sDeploymentRunnable;
                    }
                    throw new CoreRuntimeException("Deploy Store is not available");
                }
                case TaskJobSpec.KIND -> {
                    if (jobRunnableStore != null) {
                        K8sJobRunnable k8sJobRunnable = jobRunnableStore.find(run.getId());
                        if (k8sJobRunnable == null) {
                            throw new NoSuchEntityException("JobDeployment not found");
                        }
                        k8sJobRunnable.setState(State.STOP.name());
                        yield k8sJobRunnable;
                    }
                    throw new CoreRuntimeException("Job Store is not available");
                }
                case TaskServeSpec.KIND -> {
                    if (serveRunnableStore != null) {
                        K8sServeRunnable k8sServeRunnable = serveRunnableStore.find(run.getId());
                        if (k8sServeRunnable == null) {
                            throw new NoSuchEntityException("ServeDeployment not found");
                        }
                        k8sServeRunnable.setState(State.STOP.name());
                        yield k8sServeRunnable;
                    }

                    throw new CoreRuntimeException("Serve Store is not available");
                }
                case TaskBuildSpec.KIND -> {
                    if (buildRunnableStore != null) {
                        K8sKanikoRunnable k8sKanikoRunnable = buildRunnableStore.find(run.getId());
                        if (k8sKanikoRunnable == null) {
                            throw new NoSuchEntityException("Build runnable not found");
                        }
                        k8sKanikoRunnable.setState(State.STOP.name());
                        yield k8sKanikoRunnable;
                    }
                    throw new CoreRuntimeException("Build Store is not available");
                }
                default -> throw new IllegalArgumentException("Kind not recognized. Cannot retrieve the right Runner");
            };
        } catch (StoreException e) {
            log.error("Error stopping run", e);
            throw new NoSuchEntityException("Error stopping run", e);
        }
    }

    @Override
    public RunRunnable delete(Run run) throws NoSuchEntityException {
        //check run kind
        if (!RunContainerSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), RunContainerSpec.KIND)
            );
        }

        RunContainerSpec runContainerSpec = new RunContainerSpec(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunUtils.parseTask(runContainerSpec.getTask());

        try {
            return switch (runAccessor.getTask()) {
                case TaskDeploySpec.KIND -> {
                    if (deployRunnableStore != null) {
                        K8sDeploymentRunnable k8sDeploymentRunnable = deployRunnableStore.find(run.getId());
                        if (k8sDeploymentRunnable == null) {
                            //not in store, either not existent or already removed, nothing to do
                            yield null;
                        }
                        k8sDeploymentRunnable.setState(State.DELETING.name());
                        yield k8sDeploymentRunnable;
                    }
                    throw new CoreRuntimeException("Deploy Store is not available");
                }
                case TaskJobSpec.KIND -> {
                    if (jobRunnableStore != null) {
                        K8sJobRunnable k8sJobRunnable = jobRunnableStore.find(run.getId());
                        if (k8sJobRunnable == null) {
                            //not in store, either not existent or already removed, nothing to do
                            yield null;
                        }
                        k8sJobRunnable.setState(State.DELETING.name());
                        yield k8sJobRunnable;
                    }
                    throw new CoreRuntimeException("Job Store is not available");
                }
                case TaskServeSpec.KIND -> {
                    if (serveRunnableStore != null) {
                        K8sServeRunnable k8sServeRunnable = serveRunnableStore.find(run.getId());
                        if (k8sServeRunnable == null) {
                            //not in store, either not existent or already removed, nothing to do
                            yield null;
                        }
                        k8sServeRunnable.setState(State.DELETING.name());
                        yield k8sServeRunnable;
                    }
                    throw new CoreRuntimeException("Serve Store is not available");
                }
                case TaskBuildSpec.KIND -> {
                    if (buildRunnableStore != null) {
                        K8sKanikoRunnable k8sKanikoRunnable = buildRunnableStore.find(run.getId());
                        if (k8sKanikoRunnable == null) {
                            //not in store, either not existent or already removed, nothing to do
                            yield null;
                        }
                        k8sKanikoRunnable.setState(State.DELETING.name());
                        yield k8sKanikoRunnable;
                    }
                    throw new CoreRuntimeException("Build Store is not available");
                }
                default -> throw new IllegalArgumentException("Kind not recognized. Cannot retrieve the right Runner");
            };
        } catch (StoreException e) {
            log.error("Error deleting run", e);
            throw new NoSuchEntityException("Error deleting run", e);
        }
    }


    @Override
    public RunContainerStatus onComplete(Run run, RunRunnable runnable) {
        RunContainerSpec runContainerSpec = new RunContainerSpec(run.getSpec());
        RunSpecAccessor runAccessor = RunUtils.parseTask(runContainerSpec.getTask());

        //update image name after build
        if (runnable instanceof K8sKanikoRunnable) {
            String image = ((K8sKanikoRunnable) runnable).getImage();

            String functionId = runAccessor.getVersion();
            Function function = functionService.getFunction(functionId);

            log.debug("update function {} spec to use built image: {}", functionId, image);

            FunctionContainerSpec funSpec = new FunctionContainerSpec(function.getSpec());
            if (!image.equals(funSpec.getImage())) {
                funSpec.setImage(image);
                function.setSpec(funSpec.toMap());
                functionService.updateFunction(functionId, function, true);
            }
        }
        return null;
    }


    @Override
    public RunContainerStatus onDeleted(Run run, RunRunnable runnable) {
        if (runnable != null) {
            try {
                RunnableStore<?> store =
                    switch (runnable.getFramework()) {
                        case K8sDeploymentFramework.FRAMEWORK -> {
                            yield deployRunnableStore;
                        }
                        case K8sJobFramework.FRAMEWORK -> {
                            yield jobRunnableStore;
                        }
                        case K8sServeFramework.FRAMEWORK -> {
                            yield serveRunnableStore;
                        }
                        case K8sKanikoFramework.FRAMEWORK -> {
                            yield buildRunnableStore;
                        }
                        default -> null;
                    };

                if (store != null && store.find(runnable.getId()) != null) {
                    store.remove(runnable.getId());
                }
            } catch (StoreException e) {
                log.error("Error deleting runnable", e);
                throw new NoSuchEntityException("Error deleting runnable", e);
            }
        }
        return null;
    }
}
