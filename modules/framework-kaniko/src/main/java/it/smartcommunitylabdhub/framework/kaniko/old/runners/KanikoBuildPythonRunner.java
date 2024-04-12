package it.smartcommunitylabdhub.framework.kaniko.old.runners;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Runner;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.framework.k8s.base.K8sTaskSpec;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sKanikoRunnable;
import it.smartcommunitylabdhub.runtime.kaniko.KanikoRuntime;
import it.smartcommunitylabdhub.runtime.kaniko.specs.run.RunKanikoSpec;
import it.smartcommunitylabdhub.runtime.kaniko.specs.task.TaskBuildPythonSpec;
import java.util.*;

/**
 * KanikoBuildRunner
 * <p>
 * You can use this as a simple class or as a registered bean. If you want to retrieve this as bean from RunnerFactory
 * you have to register it using the following annotation:
 *
 * @RunnerComponent(runtime = "dbt", task = "transform")
 */
public class KanikoBuildPythonRunner implements Runner<K8sKanikoRunnable> {

    private static final String TASK = "build";

    private final String image;
    private final Map<String, Set<String>> groupedSecrets;

    public KanikoBuildPythonRunner(String image, Map<String, Set<String>> groupedSecrets) {
        this.image = image;
        this.groupedSecrets = groupedSecrets;
    }

    @Override
    public K8sKanikoRunnable produce(Run run) {
        // Retrieve information about RunKanikoSpec
        RunKanikoSpec runSpec = new RunKanikoSpec(run.getSpec());
        TaskBuildPythonSpec taskSpec = runSpec.getTaskBuildPythonSpec();
        if (taskSpec == null) {
            throw new CoreRuntimeException("null or empty task definition");
        }

        StatusFieldAccessor statusFieldAccessor = StatusFieldAccessor.with(run.getStatus());
        K8sTaskSpec k8s = taskSpec.getK8s() != null ? taskSpec.getK8s() : new K8sTaskSpec();

        List<CoreEnv> coreEnvList = new ArrayList<>(
                List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
        );

        Optional.ofNullable(k8s.getEnvs()).ifPresent(coreEnvList::addAll);

        //TODO: Create runnable using information from Run completed spec.
        K8sKanikoRunnable k8sBuildRunnable = K8sKanikoRunnable
                .builder()
                .runtime(KanikoRuntime.RUNTIME)
                .task(TASK)
                .image(image)
                .command("python")
                .args(List.of("wrapper.py").toArray(String[]::new))
                .resources(k8s.getResources())
                .nodeSelector(k8s.getNodeSelector())
                .volumes(k8s.getVolumes())
                .secrets(groupedSecrets)
                .envs(coreEnvList)
                .labels(k8s.getLabels())
                .affinity(k8s.getAffinity())
                .tolerations(k8s.getTolerations())
                .state(State.READY.name())
                .build();

        k8sBuildRunnable.setId(run.getId());
        k8sBuildRunnable.setProject(run.getProject());

        return k8sBuildRunnable;
    }
}
