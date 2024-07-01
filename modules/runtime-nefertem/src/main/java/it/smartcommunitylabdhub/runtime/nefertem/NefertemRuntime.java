package it.smartcommunitylabdhub.runtime.nefertem;

import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.infrastructure.Runtime;
import it.smartcommunitylabdhub.commons.models.base.Executable;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.models.entities.task.Task;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.utils.RunUtils;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.commons.services.entities.SecretService;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.runtime.nefertem.builders.NefertemInferBuilder;
import it.smartcommunitylabdhub.runtime.nefertem.builders.NefertemMetricBuilder;
import it.smartcommunitylabdhub.runtime.nefertem.builders.NefertemProfileBuilder;
import it.smartcommunitylabdhub.runtime.nefertem.builders.NefertemValidateBuilder;
import it.smartcommunitylabdhub.runtime.nefertem.runners.NefertemInferRunner;
import it.smartcommunitylabdhub.runtime.nefertem.runners.NefertemMetricRunner;
import it.smartcommunitylabdhub.runtime.nefertem.runners.NefertemProfileRunner;
import it.smartcommunitylabdhub.runtime.nefertem.runners.NefertemValidateRunner;
import it.smartcommunitylabdhub.runtime.nefertem.specs.function.FunctionNefertemSpec;
import it.smartcommunitylabdhub.runtime.nefertem.specs.run.RunNefertemSpec;
import it.smartcommunitylabdhub.runtime.nefertem.specs.task.TaskInferSpec;
import it.smartcommunitylabdhub.runtime.nefertem.specs.task.TaskMetricSpec;
import it.smartcommunitylabdhub.runtime.nefertem.specs.task.TaskProfileSpec;
import it.smartcommunitylabdhub.runtime.nefertem.specs.task.TaskValidateSpec;
import it.smartcommunitylabdhub.runtime.nefertem.status.RunNefertemStatus;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@RuntimeComponent(runtime = NefertemRuntime.RUNTIME)
@Slf4j
public class NefertemRuntime
    implements Runtime<FunctionNefertemSpec, RunNefertemSpec, RunNefertemStatus, K8sJobRunnable> {

    public static final String RUNTIME = "nefertem";

    private final NefertemInferBuilder inferBuilder = new NefertemInferBuilder();
    private final NefertemMetricBuilder metricBuilder = new NefertemMetricBuilder();
    private final NefertemValidateBuilder validateBuilder = new NefertemValidateBuilder();
    private final NefertemProfileBuilder profileBuilder = new NefertemProfileBuilder();

    @Autowired
    SecretService secretService;

    @Autowired(required = false)
    private RunnableStore<K8sJobRunnable> jobRunnableStore;

    @Value("${runtime.nefertem.image}")
    private String image;

    @Override
    public RunNefertemSpec build(@NotNull Executable function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!RunNefertemSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), RunNefertemSpec.KIND)
            );
        }

        FunctionNefertemSpec functionSpec = new FunctionNefertemSpec(function.getSpec());
        RunNefertemSpec runSpec = new RunNefertemSpec(run.getSpec());

        String kind = task.getKind();

        // Retrieve builder using task kind
        switch (kind) {
            case TaskInferSpec.KIND -> {
                TaskInferSpec taskInferSpec = new TaskInferSpec(task.getSpec());
                return inferBuilder.build(functionSpec, taskInferSpec, runSpec);
            }
            case TaskValidateSpec.KIND -> {
                TaskValidateSpec taskValidateSpec = new TaskValidateSpec(task.getSpec());
                return validateBuilder.build(functionSpec, taskValidateSpec, runSpec);
            }
            case TaskProfileSpec.KIND -> {
                TaskProfileSpec taskProfileSpec = new TaskProfileSpec(task.getSpec());
                return profileBuilder.build(functionSpec, taskProfileSpec, runSpec);
            }
            case TaskMetricSpec.KIND -> {
                TaskMetricSpec taskMetricSpec = new TaskMetricSpec(task.getSpec());
                return metricBuilder.build(functionSpec, taskMetricSpec, runSpec);
            }
            default -> throw new IllegalArgumentException(
                "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
            );
        }
    }

    @Override
    public K8sJobRunnable run(Run run) {
        //check run kind
        if (!RunNefertemSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), RunNefertemSpec.KIND)
            );
        }

        // Create spec for run
        RunNefertemSpec runSpec = new RunNefertemSpec(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunUtils.parseTask(runSpec.getTask());

        return switch (runAccessor.getTask()) {
            case TaskInferSpec.KIND -> new NefertemInferRunner(
                image,
                secretService.groupSecrets(run.getProject(), runSpec.getTaskInferSpec().getSecrets())
            )
                .produce(run);
            case TaskValidateSpec.KIND -> new NefertemValidateRunner(
                image,
                secretService.groupSecrets(run.getProject(), runSpec.getTaskValidateSpec().getSecrets())
            )
                .produce(run);
            case TaskProfileSpec.KIND -> new NefertemProfileRunner(
                image,
                secretService.groupSecrets(run.getProject(), runSpec.getTaskProfileSpec().getSecrets())
            )
                .produce(run);
            case TaskMetricSpec.KIND -> new NefertemMetricRunner(
                image,
                secretService.groupSecrets(run.getProject(), runSpec.getTaskMetricSpec().getSecrets())
            )
                .produce(run);
            default -> throw new IllegalArgumentException("Kind not recognized. Cannot retrieve the right Runner");
        };
    }

    @Override
    public K8sJobRunnable stop(Run run) {
        //check run kind
        if (!RunNefertemSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), RunNefertemSpec.KIND)
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
        if (!RunNefertemSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), RunNefertemSpec.KIND)
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
    public RunNefertemStatus onDeleted(Run run, RunRunnable runnable) {
        if (runnable != null) {
            try {
                if (jobRunnableStore != null && jobRunnableStore.find(runnable.getId()) != null) {
                    jobRunnableStore.remove(runnable.getId());
                }
            } catch (StoreException e) {
                log.error("Error deleting runnable", e);
                throw new NoSuchEntityException("Error deleting runnable", e);
            }
        }

        return null;
    }
}
