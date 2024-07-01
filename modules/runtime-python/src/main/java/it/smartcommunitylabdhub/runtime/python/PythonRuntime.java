package it.smartcommunitylabdhub.runtime.python;

import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.infrastructure.Runtime;
import it.smartcommunitylabdhub.commons.models.base.Executable;
import it.smartcommunitylabdhub.commons.models.entities.function.Function;
import it.smartcommunitylabdhub.commons.models.entities.log.Log;
import it.smartcommunitylabdhub.commons.models.entities.log.LogSpec;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.models.entities.task.Task;
import it.smartcommunitylabdhub.commons.models.entities.task.TaskBaseSpec;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.utils.RunUtils;
import it.smartcommunitylabdhub.commons.services.LogService;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.commons.services.entities.FunctionService;
import it.smartcommunitylabdhub.commons.services.entities.SecretService;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sJobFramework;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sServeFramework;
import it.smartcommunitylabdhub.framework.k8s.model.K8sLogStatus;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLog;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreMetric;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sServeRunnable;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sKanikoRunnable;
import it.smartcommunitylabdhub.runtime.python.runners.PythonBuildRunner;
import it.smartcommunitylabdhub.runtime.python.runners.PythonJobRunner;
import it.smartcommunitylabdhub.runtime.python.runners.PythonServeRunner;
import it.smartcommunitylabdhub.runtime.python.specs.function.PythonFunctionSpec;
import it.smartcommunitylabdhub.runtime.python.specs.run.PythonRunSpec;
import it.smartcommunitylabdhub.runtime.python.specs.task.PythonBuildTaskSpec;
import it.smartcommunitylabdhub.runtime.python.specs.task.PythonJobTaskSpec;
import it.smartcommunitylabdhub.runtime.python.specs.task.PythonServeTaskSpec;
import it.smartcommunitylabdhub.runtime.python.status.PythonRunStatus;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;

@Slf4j
@RuntimeComponent(runtime = PythonRuntime.RUNTIME)
public class PythonRuntime implements Runtime<PythonFunctionSpec, PythonRunSpec, PythonRunStatus, RunRunnable> {

    public static final String RUNTIME = "python";


    @Autowired
    private SecretService secretService;

    @Autowired(required = false)
    private RunnableStore<K8sJobRunnable> jobRunnableStore;

    @Autowired(required = false)
    private RunnableStore<K8sServeRunnable> serveRunnableStore;

    @Autowired(required = false)
    private RunnableStore<K8sKanikoRunnable> buildRunnableStore;

    @Autowired
    private FunctionService functionService;

    @Autowired
    private LogService logService;

    @Autowired
    @Qualifier("pythonImages")
    private Map<String, String> images;

    @Value("${runtime.python.command}")
    private String command;

    @Override
    public PythonRunSpec build(@NotNull Executable function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!PythonRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), PythonRunSpec.KIND)
            );
        }

        PythonFunctionSpec funSpec = new PythonFunctionSpec(function.getSpec());
        PythonRunSpec runSpec = new PythonRunSpec(run.getSpec());

        String kind = task.getKind();

        //build task spec as defined
        TaskBaseSpec taskSpec =
            switch (kind) {
                case PythonJobTaskSpec.KIND -> {
                    yield new PythonJobTaskSpec(task.getSpec());
                }
                case PythonServeTaskSpec.KIND -> {
                    yield new PythonServeTaskSpec(task.getSpec());
                }
                case PythonBuildTaskSpec.KIND -> {
                    yield new PythonBuildTaskSpec(task.getSpec());
                }
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build run merging task spec overrides
        Map<String, Serializable> map = new HashMap<>();
        map.putAll(runSpec.toMap());
        taskSpec.toMap().forEach(map::putIfAbsent);

        PythonRunSpec pythonSpec = new PythonRunSpec(map);
        //ensure function is not modified
        pythonSpec.setFunctionSpec(funSpec);

        return pythonSpec;
    }

    @Override
    public RunRunnable run(@NotNull Run run) {
        //check run kind
        if (!PythonRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), PythonRunSpec.KIND)
            );
        }

        PythonRunSpec runPythonSpec = new PythonRunSpec(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunUtils.parseTask(runPythonSpec.getTask());

        return switch (runAccessor.getTask()) {
            case PythonJobTaskSpec.KIND -> new PythonJobRunner(
                images.get(runPythonSpec.getFunctionSpec().getPythonVersion().name()),
                command,
                runPythonSpec.getFunctionSpec(),
                secretService.groupSecrets(run.getProject(), runPythonSpec.getTaskJobSpec().getSecrets())
            )
                .produce(run);
            case PythonServeTaskSpec.KIND -> new PythonServeRunner(
                images.get(runPythonSpec.getFunctionSpec().getPythonVersion().name()),
                command,
                runPythonSpec.getFunctionSpec(),
                secretService.groupSecrets(run.getProject(), runPythonSpec.getTaskJobSpec().getSecrets())
            )
                .produce(run);
            case PythonBuildTaskSpec.KIND -> new PythonBuildRunner(
                images.get(runPythonSpec.getFunctionSpec().getPythonVersion().name()),
                command,
                runPythonSpec.getFunctionSpec(),
                secretService.groupSecrets(run.getProject(), runPythonSpec.getTaskBuildSpec().getSecrets())
            )
                .produce(run);
            default -> throw new IllegalArgumentException("Kind not recognized. Cannot retrieve the right Runner");
        };
    }

    @Override
    public RunRunnable stop(Run run) throws NoSuchEntityException {
        //check run kind
        if (!PythonRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), PythonRunSpec.KIND)
            );
        }

        PythonRunSpec runPythonSpec = new PythonRunSpec(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunUtils.parseTask(runPythonSpec.getTask());

        try {
            return switch (runAccessor.getTask()) {
                case PythonJobTaskSpec.KIND -> {
                    if (jobRunnableStore != null) {
                        K8sJobRunnable k8sJobRunnable = jobRunnableStore.find(run.getId());
                        if (k8sJobRunnable == null) {
                            throw new NoSuchEntityException("Run not found");
                        }
                        k8sJobRunnable.setState(State.STOP.name());
                        yield k8sJobRunnable;
                    }
                    throw new CoreRuntimeException("Store is not available");
                }
                case PythonServeTaskSpec.KIND -> {
                    if (serveRunnableStore != null) {
                        K8sServeRunnable k8sServeRunnable = serveRunnableStore.find(run.getId());
                        if (k8sServeRunnable == null) {
                            throw new NoSuchEntityException("Run not found");
                        }
                        k8sServeRunnable.setState(State.STOP.name());
                        yield k8sServeRunnable;
                    }
                    throw new CoreRuntimeException("Store is not available");
                }
                case PythonBuildTaskSpec.KIND -> {
                    if (jobRunnableStore != null) {
                        K8sKanikoRunnable k8sKanikoRunnable = buildRunnableStore.find(run.getId());
                        if (k8sKanikoRunnable == null) {
                            throw new NoSuchEntityException("JobDeployment not found");
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
        if (!PythonRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), PythonRunSpec.KIND)
            );
        }

        PythonRunSpec runPythonSpec = new PythonRunSpec(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunUtils.parseTask(runPythonSpec.getTask());

        try {
            return switch (runAccessor.getTask()) {
                case PythonJobTaskSpec.KIND -> {
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
                case PythonBuildTaskSpec.KIND -> {
                    if (jobRunnableStore != null) {
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
                case PythonServeTaskSpec.KIND -> {
                    if (jobRunnableStore != null) {
                        K8sServeRunnable k8sServeRunnable = serveRunnableStore.find(run.getId());
                        if (k8sServeRunnable == null) {
                            //not in store, either not existent or already removed, nothing to do
                            yield null;
                        }
                        k8sServeRunnable.setState(State.DELETING.name());
                        yield k8sServeRunnable;
                    }
                    throw new CoreRuntimeException("Job Store is not available");
                }
                default -> throw new IllegalArgumentException("Kind not recognized. Cannot retrieve the right Runner");
            };
        } catch (StoreException e) {
            log.error("Error deleting run", e);
            throw new NoSuchEntityException("Error deleting run", e);
        }
    }

    @Override
    public PythonRunStatus onRunning(Run run, RunRunnable runnable) {
        return null;
    }

    @Override
    public PythonRunStatus onComplete(Run run, RunRunnable runnable) {
        PythonRunSpec pythonRunSpec = new PythonRunSpec(run.getSpec());
        RunSpecAccessor runAccessor = RunUtils.parseTask(pythonRunSpec.getTask());

        //update image name after build
        if (runnable instanceof K8sKanikoRunnable) {
            String image = ((K8sKanikoRunnable) runnable).getImage();

            String functionId = runAccessor.getVersion();
            Function function = functionService.getFunction(functionId);

            log.debug("update function {} spec to use built image: {}", functionId, image);

            PythonFunctionSpec funSpec = new PythonFunctionSpec(function.getSpec());
            if (!image.equals(funSpec.getImage())) {
                funSpec.setImage(image);
                function.setSpec(funSpec.toMap());
                functionService.updateFunction(functionId, function, true);
            }
        }

        return null;
    }

    @Override
    public PythonRunStatus onError(Run run, RunRunnable runnable) {
        return null;
    }

    @Override
    public PythonRunStatus onStopped(Run run, RunRunnable runnable) {
        return null;
    }

    @Override
    public PythonRunStatus onDeleted(Run run, RunRunnable runnable) {
        if (runnable != null) {
            try {
                RunnableStore<?> store =
                    switch (runnable.getFramework()) {
                        case K8sJobFramework.FRAMEWORK -> {
                            yield jobRunnableStore;
                        }
                        case K8sServeFramework.FRAMEWORK -> {
                            yield serveRunnableStore;
                        }
                        default -> {
                            yield null;
                        }
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
