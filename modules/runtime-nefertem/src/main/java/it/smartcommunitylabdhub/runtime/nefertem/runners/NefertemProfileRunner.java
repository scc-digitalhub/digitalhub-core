package it.smartcommunitylabdhub.runtime.nefertem.runners;

import it.smartcommunitylabdhub.commons.infrastructure.factories.runners.Runner;
import it.smartcommunitylabdhub.commons.models.accessors.fields.RunFieldAccessor;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.utils.jackson.JacksonMapper;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.runtime.nefertem.NefertemRuntime;
import it.smartcommunitylabdhub.runtime.nefertem.models.specs.run.RunNefertemSpec;
import java.util.*;

/**
 * DbtProfileRunner
 * <p>
 * You can use this as a simple class or as a registered bean. If you want to retrieve this as bean from RunnerFactory
 * you have to register it using the following annotation:
 *
 * @RunnerComponent(runtime = "dbt", task = "profile")
 */
public class NefertemProfileRunner implements Runner {

    private static final String TASK = "profile";
    private final String image;

    private final Map<String, Set<String>> groupedSecrets;

    public NefertemProfileRunner(String image, Map<String, Set<String>> groupedSecrets) {
        this.image = image;
        this.groupedSecrets = groupedSecrets;
    }

    @Override
    public K8sJobRunnable produce(Run runDTO) {
        // Retrieve information about Spec
        RunNefertemSpec runNefertemSpec = RunNefertemSpec.builder().build();
        runNefertemSpec.configure(runDTO.getSpec());

        RunFieldAccessor runDefaultFieldAccessor = RunFieldAccessor.with(
            JacksonMapper.CUSTOM_OBJECT_MAPPER.convertValue(runDTO, JacksonMapper.typeRef)
        );

        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", runDTO.getProject()), new CoreEnv("RUN_ID", runDTO.getId()))
        );

        Optional.ofNullable(runNefertemSpec.getTaskProfileSpec().getEnvs()).ifPresent(coreEnvList::addAll);

        //TODO: Create runnable using information from Run completed spec.
        K8sJobRunnable k8sJobRunnable = K8sJobRunnable
            .builder()
            .runtime(NefertemRuntime.RUNTIME)
            .task(TASK)
            .image(image)
            .command("python")
            .args(List.of("wrapper.py").toArray(String[]::new))
            .resources(runNefertemSpec.getTaskProfileSpec().getResources())
            .nodeSelector(runNefertemSpec.getTaskProfileSpec().getNodeSelector())
            .volumes(runNefertemSpec.getTaskProfileSpec().getVolumes())
            .secrets(groupedSecrets)
            .envs(coreEnvList)
            .labels(runNefertemSpec.getTaskProfileSpec().getLabels())
            .affinity(runNefertemSpec.getTaskProfileSpec().getAffinity())
            .tolerations(runNefertemSpec.getTaskProfileSpec().getTolerations())
            .state(runDefaultFieldAccessor.getState())
            .build();

        k8sJobRunnable.setId(runDTO.getId());
        k8sJobRunnable.setProject(runDTO.getProject());

        return k8sJobRunnable;
    }
}