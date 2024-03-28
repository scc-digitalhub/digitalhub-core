package it.smartcommunitylabdhub.runtime.kaniko;

import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.infrastructure.Runtime;
import it.smartcommunitylabdhub.commons.models.entities.function.Function;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.models.entities.task.Task;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.utils.RunUtils;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.commons.services.entities.SecretService;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.runtime.kaniko.builders.KanikoBuildJavaBuilder;
import it.smartcommunitylabdhub.runtime.kaniko.builders.KanikoBuildPythonBuilder;
import it.smartcommunitylabdhub.runtime.kaniko.specs.function.FunctionKanikoSpec;
import it.smartcommunitylabdhub.runtime.kaniko.specs.run.RunKanikoSpec;
import it.smartcommunitylabdhub.runtime.kaniko.specs.task.TaskBuildJavaSpec;
import it.smartcommunitylabdhub.runtime.kaniko.specs.task.TaskBuildPythonSpec;
import it.smartcommunitylabdhub.runtime.kaniko.status.RunKanikoStatus;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@RuntimeComponent(runtime = KanikoRuntime.RUNTIME)
@Slf4j
public class KanikoRuntime
        implements Runtime<FunctionKanikoSpec, RunKanikoSpec, RunKanikoStatus, K8sJobRunnable> {

    public static final String RUNTIME = "nefertem";

    private final KanikoBuildJavaBuilder javaBuilder = new KanikoBuildJavaBuilder();
    private final KanikoBuildPythonBuilder pythonBuilder = new KanikoBuildPythonBuilder();

    @Autowired
    SecretService secretService;

    @Autowired(required = false)
    private RunnableStore<K8sJobRunnable> jobRunnableStore;

    @Value("${runtime.nefertem.image}")
    private String image;

    @Override
    public RunKanikoSpec build(@NotNull Function function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!RunKanikoSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                    "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), RunKanikoSpec.KIND)
            );
        }

        FunctionKanikoSpec functionSpec = new FunctionKanikoSpec(function.getSpec());
        RunKanikoSpec runSpec = new RunKanikoSpec(run.getSpec());

        String kind = task.getKind();

        // Retrieve builder using task kind
        switch (kind) {
            case TaskBuildJavaSpec.KIND -> {
                TaskBuildJavaSpec taskBuildJavaSpec = new TaskBuildJavaSpec(task.getSpec());
                return javaBuilder.build(functionSpec, taskBuildJavaSpec, runSpec);
            }
            case TaskBuildPythonSpec.KIND -> {
                TaskBuildPythonSpec taskBuildPythonSpec = new TaskBuildPythonSpec(task.getSpec());
                return pythonBuilder.build(functionSpec, taskBuildPythonSpec, runSpec);
            }
            default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
            );
        }
    }

    @Override
    public K8sJobRunnable run(Run run) {
        //check run kind
        if (!RunKanikoSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                    "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), RunKanikoSpec.KIND)
            );
        }

        // Create spec for run
        RunKanikoSpec runSpec = new RunKanikoSpec(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunUtils.parseTask(runSpec.getTask());

        return switch (runAccessor.getTask()) {
            case TaskBuildJavaSpec.KIND -> new KanikoBuildJavaRunner(
                    image,
                    secretService.groupSecrets(run.getProject(), runSpec.getTaskBuildJavaSpec().getK8s().getSecrets())
            )
                    .produce(run);
            case TaskBuildPythonSpec.KIND -> new KanikoBuildPythonRunner(
                    image,
                    secretService.groupSecrets(run.getProject(), runSpec.getTaskBuildPythonSpec().getK8s().getSecrets())
            )
                    .produce(run);
            case TaskProfileSpec.KIND -> new KanikoProfileRunner(
                    image,
                    secretService.groupSecrets(run.getProject(), runSpec.getTaskProfileSpec().getK8s().getSecrets())
            )
                    .produce(run);
            case TaskBuildPythonSpec.KIND -> new KanikoBuildPythonRunner(
                    image,
                    secretService.groupSecrets(run.getProject(), runSpec.getTaskBuildPythonSpec().getK8s().getSecrets())
            )
                    .produce(run);
            default -> throw new IllegalArgumentException("Kind not recognized. Cannot retrieve the right Runner");
        };
    }

    @Override
    public K8sJobRunnable stop(Run run) {
        //check run kind
        if (!RunKanikoSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                    "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), RunKanikoSpec.KIND)
            );
        }

        if (jobRunnableStore == null) {
            throw new CoreRuntimeException("Job Store is not available");
        }
        try {
            K8sJobRunnable k8sJobRunnable = jobRunnableStore.find(run.getId());
            if (k8sJobRunnable == null) {
                throw new NoSuchEntityException("JobRunnable not found");
            }

            //set state to STOP to signal framework to stop the runnable
            k8sJobRunnable.setState(State.STOP.name());

            return k8sJobRunnable;
        } catch (StoreException e) {
            log.error("Error stopping run", e);
            throw new NoSuchEntityException("Error stopping run", e);
        }
    }

    @Override
    public K8sJobRunnable delete(Run run) {
        //check run kind
        if (!RunKanikoSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                    "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), RunKanikoSpec.KIND)
            );
        }
        if (jobRunnableStore == null) {
            throw new CoreRuntimeException("Job Store is not available");
        }
        try {
            K8sJobRunnable k8sJobRunnable = jobRunnableStore.find(run.getId());
            if (k8sJobRunnable == null) {
                throw new NoSuchEntityException("JobRunnable not found");
            }

            //set state to DELETING to signal framework to delete the runnable
            k8sJobRunnable.setState(State.DELETING.name());

            return k8sJobRunnable;
        } catch (StoreException e) {
            log.error("Error stopping run", e);
            throw new NoSuchEntityException("Error stopping run", e);
        }
    }

    @Override
    public RunKanikoStatus onRunning(Run run, RunRunnable runnable) {
        return null;
    }

    @Override
    public RunKanikoStatus onComplete(Run run, RunRunnable runnable) {
        if (runnable != null) {
            cleanup(runnable);
        }

        return null;
    }

    @Override
    public RunKanikoStatus onError(Run run, RunRunnable runnable) {
        if (runnable != null) {
            cleanup(runnable);
        }

        return null;
    }

    @Override
    public RunKanikoStatus onStopped(Run run, RunRunnable runnable) {
        if (runnable != null) {
            cleanup(runnable);
        }

        return null;
    }

    @Override
    public RunKanikoStatus onDeleted(Run run, RunRunnable runnable) {
        if (runnable != null) {
            cleanup(runnable);
        }

        return null;
    }

    private void cleanup(RunRunnable runnable) {
        try {
            if (jobRunnableStore != null && jobRunnableStore.find(runnable.getId()) != null) {
                jobRunnableStore.remove(runnable.getId());
            }
        } catch (StoreException e) {
            log.error("Error deleting runnable", e);
            throw new NoSuchEntityException("Error deleting runnable", e);
        }
    }
}
