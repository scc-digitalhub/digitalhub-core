package it.smartcommunitylabdhub.runtime.modelserve.runners;

import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Runner;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.utils.TaskUtils;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CorePort;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreServiceType;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sServeRunnable;
import it.smartcommunitylabdhub.runtime.modelserve.SklearnServeRuntime;
import it.smartcommunitylabdhub.runtime.modelserve.models.MLFlowSettingsParameters;
import it.smartcommunitylabdhub.runtime.modelserve.models.MLFlowSettingsSpec;
import it.smartcommunitylabdhub.runtime.modelserve.specs.MlflowServeFunctionSpec;
import it.smartcommunitylabdhub.runtime.modelserve.specs.MlflowServeRunSpec;
import it.smartcommunitylabdhub.runtime.modelserve.specs.MlflowServeTaskSpec;
import it.smartcommunitylabdhub.runtime.modelserve.specs.ModelServeFunctionSpec;
import it.smartcommunitylabdhub.runtime.modelserve.specs.ModelServeServeTaskSpec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

public class MlflowServeRunner implements Runner<K8sRunnable> {

    private static final int HTTP_PORT = 8080;
    private static final int GRPC_PORT = 8081;

    private final String image;
    private final ModelServeFunctionSpec functionSpec;
    private final Map<String, Set<String>> groupedSecrets;

    private final K8sBuilderHelper k8sBuilderHelper;

    public MlflowServeRunner(
        String image,
        MlflowServeFunctionSpec functionSpec,
        Map<String, Set<String>> groupedSecrets,
        K8sBuilderHelper k8sBuilderHelper
    ) {
        this.image = image;
        this.functionSpec = functionSpec;
        this.groupedSecrets = groupedSecrets;
        this.k8sBuilderHelper = k8sBuilderHelper;
    }

    @Override
    public K8sRunnable produce(Run run) {
        MlflowServeRunSpec runSpec = MlflowServeRunSpec.with(run.getSpec());
        ModelServeServeTaskSpec taskSpec = runSpec.getTaskServeSpec();
        TaskSpecAccessor taskAccessor = TaskUtils.parseFunction(taskSpec.getFunction());

        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
        );

        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

        UriComponents uri = UriComponentsBuilder.fromUriString(functionSpec.getPath()).build();
        String source = functionSpec.getPath().trim();
        if (!source.endsWith("/")) source += "/";

        //read source and build context
        List<ContextRef> contextRefs = Collections.singletonList(
            ContextRef.builder()
            .source(source)
            .protocol(uri.getScheme())
            .destination("model")
            .build());
        List<ContextSource> contextSources = new ArrayList<>();

        MLFlowSettingsSpec mlFlowSettingsSpec = MLFlowSettingsSpec
            .builder()
            .name(StringUtils.hasText(functionSpec.getModelName()) ? functionSpec.getModelName() : "model")
            .implementation("mlserver_mlflow.MLflowRuntime")
            // .platform()
            .parameters(
                MLFlowSettingsParameters
                    .builder()
                    .uri("./model")
                    // .contentType()
                    .build()
            )
            .build();

        //write model settings
        try {
            String setttingsString = JacksonMapper.CUSTOM_OBJECT_MAPPER.writeValueAsString(mlFlowSettingsSpec);
            ContextSource entry = ContextSource
                .builder()
                .name("model-settings.json")
                .base64(Base64.getEncoder().encodeToString(setttingsString.getBytes()))
                .build();
            contextSources.add(entry);
        } catch (IOException ioe) {
            throw new CoreRuntimeException("error with reading entrypoint for runtime-mlflow");
        }

        List<String> args = new ArrayList<>(
            List.of("start", "/shared")
        );

        CorePort servicePort = new CorePort(HTTP_PORT, HTTP_PORT);
        CorePort grpcPort = new CorePort(GRPC_PORT, GRPC_PORT);

        String img = StringUtils.hasText(functionSpec.getImage()) ? functionSpec.getImage() : image;
        
        //build runnable
        K8sRunnable k8sServeRunnable = K8sServeRunnable
            .builder()
            .runtime(SklearnServeRuntime.RUNTIME)
            .task(MlflowServeTaskSpec.KIND)
            .state(State.READY.name())
            .labels(
                k8sBuilderHelper != null
                    ? List.of(new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction()))
                    : null
            )
            //base
            .image(img)
            .command("mlserver")
            .args(args.toArray(new String[0]))
            .contextSources(contextSources)
            .contextRefs(contextRefs)
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
            //specific
            .replicas(taskSpec.getReplicas())
            .servicePorts(List.of(servicePort, grpcPort))
            .serviceType(taskSpec.getServiceType() != null ? taskSpec.getServiceType() : CoreServiceType.NodePort)
            .build();

        k8sServeRunnable.setId(run.getId());
        k8sServeRunnable.setProject(run.getProject());

        return k8sServeRunnable;
    }
}
