package it.smartcommunitylabdhub.runtime.container.runners;

import it.smartcommunitylabdhub.commons.infrastructure.factories.runners.Runner;
import it.smartcommunitylabdhub.commons.models.accessors.fields.RunFieldAccessor;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.utils.jackson.JacksonMapper;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sDeploymentRunnable;
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
 * ContainerDeployRunner
 * <p>
 * You can use this as a simple class or as a registered bean. If you want to retrieve this as bean from RunnerFactory
 * you have to register it using the following annotation:
 *
 * @RunnerComponent(runtime = "container", task = "deploy")
 */
public class ContainerDeployRunner implements Runner {

  private static final String TASK = "deploy";

  private final FunctionContainerSpec functionContainerSpec;
  private final Map<String, Set<String>> groupedSecrets;

  public ContainerDeployRunner(
    FunctionContainerSpec functionContainerSpec,
    Map<String, Set<String>> groupedSecrets
  ) {
    this.functionContainerSpec = functionContainerSpec;
    this.groupedSecrets = groupedSecrets;
  }

  @Override
  public K8sDeploymentRunnable produce(Run runDTO) {
    // Retrieve information about RunDbtSpec

    RunContainerSpec runContainerSpec = RunContainerSpec.builder().build();
    runContainerSpec.configure(runDTO.getSpec());

    RunFieldAccessor runDefaultFieldAccessor = RunFieldAccessor.with(
      JacksonMapper.CUSTOM_OBJECT_MAPPER.convertValue(
        runDTO,
        JacksonMapper.typeRef
      )
    );

    List<CoreEnv> coreEnvList = new ArrayList<>(
      List.of(
        new CoreEnv("PROJECT_NAME", runDTO.getProject()),
        new CoreEnv("RUN_ID", runDTO.getId())
      )
    );

    if (
      runContainerSpec.getTaskDeploySpec().getEnvs() != null
    ) coreEnvList.addAll(runContainerSpec.getTaskDeploySpec().getEnvs());

    K8sDeploymentRunnable k8sDeploymentRunnable = K8sDeploymentRunnable
      .builder()
      .runtime(ContainerRuntime.RUNTIME) //TODO: delete accessor.
      .task(TASK)
      .image(functionContainerSpec.getImage())
      .state(runDefaultFieldAccessor.getState())
      .resources(runContainerSpec.getTaskDeploySpec().getResources())
      .nodeSelector(runContainerSpec.getTaskDeploySpec().getNodeSelector())
      .volumes(runContainerSpec.getTaskDeploySpec().getVolumes())
      .secrets(groupedSecrets)
      .envs(coreEnvList)
      .build();

    Optional
      .ofNullable(functionContainerSpec.getArgs())
      .ifPresent(args ->
        k8sDeploymentRunnable.setArgs(
          args
            .stream()
            .filter(Objects::nonNull)
            .map(Object::toString)
            .toArray(String[]::new)
        )
      );

    Optional
      .ofNullable(functionContainerSpec.getEntrypoint())
      .ifPresent(k8sDeploymentRunnable::setEntrypoint);

    k8sDeploymentRunnable.setId(runDTO.getId());
    k8sDeploymentRunnable.setProject(runDTO.getProject());

    return k8sDeploymentRunnable;
  }
}
