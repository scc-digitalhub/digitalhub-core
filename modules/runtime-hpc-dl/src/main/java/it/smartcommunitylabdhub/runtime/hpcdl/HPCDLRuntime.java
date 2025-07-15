package it.smartcommunitylabdhub.runtime.hpcdl;

import it.smartcommunitylabdhub.authorization.services.CredentialsService;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.models.artifact.Artifact;
import it.smartcommunitylabdhub.commons.models.base.Executable;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.models.task.TaskBaseSpec;
import it.smartcommunitylabdhub.commons.services.ArtifactManager;
import it.smartcommunitylabdhub.commons.services.MetricsService;
import it.smartcommunitylabdhub.commons.services.ProjectManager;
import it.smartcommunitylabdhub.files.service.FilesService;
import it.smartcommunitylabdhub.runtime.hpcdl.framework.runnables.HPCDLRunnable;
import it.smartcommunitylabdhub.runtime.hpcdl.runners.HPCDLJobRunner;
import it.smartcommunitylabdhub.runtime.hpcdl.specs.HPCDLFunctionSpec;
import it.smartcommunitylabdhub.runtime.hpcdl.specs.HPCDLJobTaskSpec;
import it.smartcommunitylabdhub.runtime.hpcdl.specs.HPCDLRunSpec;
import it.smartcommunitylabdhub.runtime.hpcdl.specs.HPCDLRunStatus;
import it.smartcommunitylabdhub.runtimes.base.AbstractBaseRuntime;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.validation.BindException;

@RuntimeComponent(runtime = HPCDLRuntime.RUNTIME)
@Slf4j
public class HPCDLRuntime extends AbstractBaseRuntime<HPCDLFunctionSpec, HPCDLRunSpec, HPCDLRunStatus, HPCDLRunnable> {

    public static final String RUNTIME = "hpcdl";

    @Autowired
    private FilesService filesService;

    @Autowired
    private ArtifactManager artifactManager;

    @Autowired
    private ProjectManager projectManager;

    @Autowired
    private CredentialsService credentialsService;

    @Autowired
    private MetricsService<Run> runMetricService;

    public HPCDLRuntime() {
        super(HPCDLRunSpec.KIND);
    }

    @Override
    public HPCDLRunSpec build(@NotNull Executable function, @NotNull Task task, @NotNull Run run) {
        if (!HPCDLRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), HPCDLRunSpec.KIND)
            );
        }

        HPCDLFunctionSpec funcSpec = new HPCDLFunctionSpec(function.getSpec());
        HPCDLRunSpec runSpec = new HPCDLRunSpec(run.getSpec());

        String kind = task.getKind();

        //build task spec as defined
        TaskBaseSpec taskSpec =
            switch (kind) {
                case HPCDLJobTaskSpec.KIND -> {
                    yield new HPCDLJobTaskSpec(task.getSpec());
                }
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build run merging task spec overrides
        Map<String, Serializable> map = new HashMap<>();
        map.putAll(runSpec.toMap());
        taskSpec.toMap().forEach(map::putIfAbsent);

        HPCDLRunSpec hpcSpec = new HPCDLRunSpec(map);
        //ensure function is not modified
        hpcSpec.setFunctionSpec(funcSpec);

        return hpcSpec;
    }

    @Override
    public HPCDLRunnable run(@NotNull Run run) {
        //check run kind
        if (!HPCDLRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), HPCDLRunSpec.KIND)
            );
        }
        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        return switch (runAccessor.getTask()) {
            case HPCDLJobTaskSpec.KIND -> new HPCDLJobRunner(filesService, artifactManager, projectManager, credentialsService).produce(run);
            default -> throw new IllegalArgumentException("Kind not recognized. Cannot retrieve the right Runner");
        };
    }

    @Override
    public HPCDLRunStatus onComplete(Run run, RunRunnable runnable) {
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        // Retrieve status when node workflow is completed
        if (
            HPCDLJobTaskSpec.KIND.equals(runAccessor.getTask()) &&
            runnable instanceof HPCDLRunnable hpcdlRunnable
        ) {
            for(String artifactId: hpcdlRunnable.getOutputArtifacts().values()) {
                Artifact artifact = artifactManager.findArtifact(artifactId);
                if (artifact != null) {
                    Map<String, Serializable> status = new HashMap<>();
                    status.put("state", State.CREATED.name());
                    artifact.setStatus(status);
                    try {
                        artifactManager.updateArtifact(artifactId, artifact);
                    } catch (NoSuchEntityException | IllegalArgumentException | SystemException | BindException e) {
                        log.error("Failed to update artifact status", e);
                    }    
                }
            }
            return HPCDLRunStatus.builder().job(hpcdlRunnable.getJob()).build();
        }

        return null;
    }

    @Override
    public HPCDLRunStatus onRunning(Run run, RunRunnable runnable) {
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        // Retrieve status when node workflow is completed
        if (
            HPCDLJobTaskSpec.KIND.equals(runAccessor.getTask()) &&
            runnable instanceof HPCDLRunnable hpcdlRunnable
        ) {
            return HPCDLRunStatus.builder().job(hpcdlRunnable.getJob()).build();
        }
        return null;
    }

    @Override
    @Nullable
    public HPCDLRunStatus onError(@NotNull Run run, RunRunnable runnable) {
        // TODO Auto-generated method stub
        return super.onError(run, runnable);
    }

    
}
