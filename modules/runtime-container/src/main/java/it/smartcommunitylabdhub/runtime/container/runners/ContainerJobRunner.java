package it.smartcommunitylabdhub.runtime.container.runners;

import it.smartcommunitylabdhub.commons.infrastructure.factories.runners.Runner;
import it.smartcommunitylabdhub.commons.models.accessors.fields.RunFieldAccessor;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.utils.jackson.JacksonMapper;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.runtime.container.ContainerRuntime;
import it.smartcommunitylabdhub.runtime.container.models.specs.function.FunctionContainerSpec;
import it.smartcommunitylabdhub.runtime.container.models.specs.run.RunContainerSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * ContainerJobRunner
 * <p>
 * You can use this as a simple class or as a registered bean. If you want to retrieve this as bean from RunnerFactory
 * you have to register it using the following annotation:
 *
 * @RunnerComponent(runtime = "container", task = "job")
 */
public class ContainerJobRunner implements Runner {

    private static final String TASK = "job";

    private final FunctionContainerSpec functionContainerSpec;
    private final Map<String, Set<String>> groupedSecrets;

    public ContainerJobRunner(FunctionContainerSpec functionContainerSpec, Map<String, Set<String>> groupedSecrets) {
        this.functionContainerSpec = functionContainerSpec;
        this.groupedSecrets = groupedSecrets;
    }

    @Override
    public K8sJobRunnable produce(Run runDTO) {
        RunContainerSpec runContainerSpec = RunContainerSpec.builder().build();
        runContainerSpec.configure(runDTO.getSpec());

        RunFieldAccessor runDefaultFieldAccessor = RunFieldAccessor.with(
            JacksonMapper.CUSTOM_OBJECT_MAPPER.convertValue(runDTO, JacksonMapper.typeRef)
        );

        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", runDTO.getProject()), new CoreEnv("RUN_ID", runDTO.getId()))
        );

        if (runContainerSpec.getTaskJobSpec().getEnvs() != null) coreEnvList.addAll(
            runContainerSpec.getTaskJobSpec().getEnvs()
        );

        K8sJobRunnable k8sJobRunnable = K8sJobRunnable
            .builder()
            .runtime(ContainerRuntime.RUNTIME)
            .task(TASK)
            .image(functionContainerSpec.getImage())
            .state(runDefaultFieldAccessor.getState())
            .resources(runContainerSpec.getTaskJobSpec().getResources())
            .nodeSelector(runContainerSpec.getTaskJobSpec().getNodeSelector())
            .volumes(runContainerSpec.getTaskJobSpec().getVolumes())
            .secrets(groupedSecrets)
            .envs(coreEnvList)
            .build();

        Optional
            .ofNullable(functionContainerSpec.getArgs())
            .ifPresent(args ->
                k8sJobRunnable.setArgs(
                    args.stream().filter(Objects::nonNull).map(Object::toString).toArray(String[]::new)
                )
            );

        Optional.ofNullable(functionContainerSpec.getCommand()).ifPresent(k8sJobRunnable::setCommand);

        k8sJobRunnable.setId(runDTO.getId());
        k8sJobRunnable.setProject(runDTO.getProject());

        return k8sJobRunnable;
    }
}
