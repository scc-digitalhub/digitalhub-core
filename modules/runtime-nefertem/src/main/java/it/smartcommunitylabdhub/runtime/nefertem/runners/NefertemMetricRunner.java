package it.smartcommunitylabdhub.runtime.nefertem.runners;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Runner;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.framework.k8s.base.K8sTaskSpec;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.runtime.nefertem.NefertemRuntime;
import it.smartcommunitylabdhub.runtime.nefertem.specs.run.RunNefertemSpec;
import it.smartcommunitylabdhub.runtime.nefertem.specs.task.TaskMetricSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * DbtMetricRunner
 * <p>
 * You can use this as a simple class or as a registered bean. If you want to retrieve this as bean from RunnerFactory
 * you have to register it using the following annotation:
 *
 * @RunnerComponent(runtime = "dbt", task = "metric")
 */
public class NefertemMetricRunner implements Runner<K8sJobRunnable> {

    private static final String TASK = "metric";
    private final String image;

    private final Map<String, Set<String>> groupedSecrets;

    public NefertemMetricRunner(String image, Map<String, Set<String>> groupedSecrets) {
        this.image = image;
        this.groupedSecrets = groupedSecrets;
    }

    @Override
    public K8sJobRunnable produce(Run run) {
        // Retrieve information about Spec
        RunNefertemSpec runSpec = new RunNefertemSpec(run.getSpec());
        TaskMetricSpec taskSpec = runSpec.getTaskMetricSpec();
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
        K8sJobRunnable k8sJobRunnable = K8sJobRunnable
            .builder()
            .runtime(NefertemRuntime.RUNTIME)
            .task(TASK)
            .image(image)
            .command("python")
            .args(List.of("wrapper.py").toArray(String[]::new))
            .resources(k8s.getResources())
            .nodeSelector(k8s.getNodeSelector())
            .volumes(k8s.getVolumes())
            .secrets(groupedSecrets)
            .envs(coreEnvList)
            .affinity(k8s.getAffinity())
            .tolerations(k8s.getTolerations())
            .state(State.READY.name())
            .build();

        k8sJobRunnable.setId(run.getId());

        k8sJobRunnable.setProject(run.getProject());
        return k8sJobRunnable;
    }
}
