package it.smartcommunitylabdhub.runtime.container.runners;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.infrastructure.Runner;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.objects.SourceCode;
import it.smartcommunitylabdhub.commons.models.utils.TaskUtils;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sServeRunnable;
import it.smartcommunitylabdhub.runtime.container.ContainerRuntime;
import it.smartcommunitylabdhub.runtime.container.specs.ContainerFunctionSpec;
import it.smartcommunitylabdhub.runtime.container.specs.ContainerFunctionSpec.SourceCodeLanguages;
import it.smartcommunitylabdhub.runtime.container.specs.ContainerRunSpec;
import it.smartcommunitylabdhub.runtime.container.specs.ContainerServeTaskSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * ContainerDeployRunner
 * <p>
 * You can use this as a simple class or as a registered bean. If you want to retrieve this as bean from RunnerFactory
 * you have to register it using the following annotation:
 *
 * @RunnerComponent(runtime = "container", task = "deploy")
 */
public class ContainerServeRunner implements Runner<K8sServeRunnable> {

    private final ContainerFunctionSpec functionSpec;
    private final Map<String, Set<String>> groupedSecrets;

    private final K8sBuilderHelper k8sBuilderHelper;

    public ContainerServeRunner(
        ContainerFunctionSpec functionContainerSpec,
        Map<String, Set<String>> groupedSecrets,
        K8sBuilderHelper k8sBuilderHelper
    ) {
        this.functionSpec = functionContainerSpec;
        this.groupedSecrets = groupedSecrets;
        this.k8sBuilderHelper = k8sBuilderHelper;
    }

    @Override
    public K8sServeRunnable produce(Run run) {
        ContainerRunSpec runSpec = new ContainerRunSpec(run.getSpec());
        ContainerServeTaskSpec taskSpec = runSpec.getTaskServeSpec();
        StatusFieldAccessor statusFieldAccessor = StatusFieldAccessor.with(run.getStatus());
        TaskSpecAccessor taskAccessor = TaskUtils.parseFunction(taskSpec.getFunction());

        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
        );

        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

        //read source and build context
        List<ContextRef> contextRefs = null;
        List<ContextSource> contextSources = null;

        if (functionSpec.getSource() != null && StringUtils.hasText(functionSpec.getSource().getSource())) {
            SourceCode<SourceCodeLanguages> source = functionSpec.getSource();

            try {
                //evaluate if local path (no scheme)
                UriComponents uri = UriComponentsBuilder.fromUriString(source.getSource()).build();
                String scheme = uri.getScheme();

                if (scheme != null) {
                    //write as ref
                    contextRefs = Collections.singletonList(ContextRef.from(source.getSource()));
                } else {
                    //write as source
                    String path = source.getSource();
                    if (StringUtils.hasText(source.getBase64())) {
                        contextSources =
                            Collections.singletonList(
                                (ContextSource.builder().name(path).base64(source.getBase64()).build())
                            );
                    }
                }
            } catch (IllegalArgumentException e) {
                //skip invalid source
            }
        }

        K8sServeRunnable k8sServeRunnable = K8sServeRunnable
            .builder()
            .runtime(ContainerRuntime.RUNTIME)
            .task(ContainerServeTaskSpec.KIND)
            .state(State.READY.name())
            .labels(
                k8sBuilderHelper != null
                    ? List.of(new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction()))
                    : null
            )
            //base
            .image(functionSpec.getImage())
            .command(functionSpec.getCommand())
            .args(functionSpec.getArgs() != null ? functionSpec.getArgs().toArray(new String[0]) : null)
            .envs(coreEnvList)
            .secrets(groupedSecrets)
            .resources(taskSpec.getResources())
            .volumes(taskSpec.getVolumes())
            .nodeSelector(taskSpec.getNodeSelector())
            .affinity(taskSpec.getAffinity())
            .tolerations(taskSpec.getTolerations())
            .runtimeClass(taskSpec.getRuntimeClass())
            .priorityClass(taskSpec.getPriorityClass())
            .template(taskSpec.getProfile())
            //securityContext
            .fsGroup(taskSpec.getFsGroup())
            //specific
            .contextRefs(contextRefs)
            .contextSources(contextSources)
            .servicePorts(taskSpec.getServicePorts())
            .serviceType(taskSpec.getServiceType())
            .build();

        k8sServeRunnable.setId(run.getId());
        k8sServeRunnable.setProject(run.getProject());

        return k8sServeRunnable;
    }
}
