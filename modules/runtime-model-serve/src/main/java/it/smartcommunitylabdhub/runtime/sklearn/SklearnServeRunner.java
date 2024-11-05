package it.smartcommunitylabdhub.runtime.sklearn;

import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.accessors.fields.KeyAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Runner;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.base.FileInfo;
import it.smartcommunitylabdhub.commons.models.base.RelationshipDetail;
import it.smartcommunitylabdhub.commons.models.entities.model.Model;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import it.smartcommunitylabdhub.commons.models.enums.RelationshipName;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.metadata.RelationshipsMetadata;
import it.smartcommunitylabdhub.commons.models.utils.TaskUtils;
import it.smartcommunitylabdhub.commons.services.entities.ModelService;
import it.smartcommunitylabdhub.commons.utils.KeyUtils;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CorePort;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreServiceType;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sServeRunnable;
import it.smartcommunitylabdhub.runtime.mlflow.models.MLServerSettingsParameters;
import it.smartcommunitylabdhub.runtime.mlflow.models.MLServerSettingsSpec;
import it.smartcommunitylabdhub.runtime.modelserve.specs.ModelServeServeTaskSpec;
import it.smartcommunitylabdhub.runtime.sklearn.specs.SklearnServeFunctionSpec;
import it.smartcommunitylabdhub.runtime.sklearn.specs.SklearnServeRunSpec;
import it.smartcommunitylabdhub.runtime.sklearn.specs.SklearnServeTaskSpec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.type.TypeReference;

public class SklearnServeRunner implements Runner<K8sRunnable> {

    private static final int HTTP_PORT = 8080;
    private static final int GRPC_PORT = 8081;

    private final String image;
    private final SklearnServeFunctionSpec functionSpec;
    private final Map<String, String> secretData;

    private final K8sBuilderHelper k8sBuilderHelper;
    private final ModelService modelService;

    public SklearnServeRunner(
        String image,
        SklearnServeFunctionSpec functionSpec,
        Map<String, String> secretData,
        K8sBuilderHelper k8sBuilderHelper, 
        ModelService modelService
    ) {
        this.image = image;
        this.functionSpec = functionSpec;
        this.secretData = secretData;
        this.k8sBuilderHelper = k8sBuilderHelper;
        this.modelService = modelService;
    }

    @Override
    public K8sRunnable produce(Run run) {
        SklearnServeRunSpec runSpec = SklearnServeRunSpec.with(run.getSpec());
        ModelServeServeTaskSpec taskSpec = runSpec.getTaskServeSpec();
        TaskSpecAccessor taskAccessor = TaskUtils.parseFunction(taskSpec.getFunction());

        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
        );

        List<CoreEnv> coreSecrets = secretData == null
            ? null
            : secretData.entrySet().stream().map(e -> new CoreEnv(e.getKey(), e.getValue())).toList();

        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

        String path = functionSpec.getPath();

        // special case: path as entity key - reference to a model
        if (functionSpec.getPath().startsWith(Keys.STORE_PREFIX)) {
            KeyAccessor keyAccessor = KeyUtils.parseKey(path, false);
            if (!EntityName.MODEL.getValue().equals(keyAccessor.getType())) {
                throw new CoreRuntimeException("invalid entity kind reference, expected model");
            }
            Model model = keyAccessor.getId() != null 
                ? modelService.findModel(keyAccessor.getId()) 
                : modelService.getLatestModel(keyAccessor.getProject(), keyAccessor.getName());
            if (model == null) {
                throw new CoreRuntimeException("invalid entity reference, sklearn model not found");
            }
            if (!model.getKind().equals("sklearn")) {
                throw new CoreRuntimeException("invalid entity reference, expected sklearn model");
            }
            RelationshipDetail rel = new RelationshipDetail();
            rel.setType(RelationshipName.CONSUMES);
            rel.setDest(run.getKey());
            rel.setSource(model.getKey());
            RelationshipsMetadata relationships = RelationshipsMetadata.from(run.getMetadata());
            relationships.getRelationships().add(rel);
            run.getMetadata().putAll(relationships.toMap());

            path = getFilePath(model);
        }

        //read source and build context
        UriComponents uri = UriComponentsBuilder.fromUriString(path).build();
        String fileName = uri.getPathSegments().getLast();

        //read source and build context
        List<ContextRef> contextRefs = Collections.singletonList(
            ContextRef.builder().source(path).protocol(uri.getScheme()).destination("model").build()
        );

        List<ContextSource> contextSources = new ArrayList<>();

        MLServerSettingsSpec mlServerSettingsSpec = MLServerSettingsSpec
            .builder()
            .name(StringUtils.hasText(functionSpec.getModelName()) ? functionSpec.getModelName() : "model")
            .implementation("mlserver_sklearn.SKLearnModel")
            // .platform()
            .parameters(
                MLServerSettingsParameters
                    .builder()
                    .uri("./model/" + fileName)
                    // .contentType()
                    .build()
            )
            .build();

        //write model settings
        try {
            ContextSource entry = ContextSource
                .builder()
                .name("model-settings.json")
                .base64(
                    Base64
                        .getEncoder()
                        .encodeToString(
                            JacksonMapper.CUSTOM_OBJECT_MAPPER.writeValueAsString(mlServerSettingsSpec).getBytes()
                        )
                )
                .build();
            contextSources.add(entry);
        } catch (IOException ioe) {
            throw new CoreRuntimeException("error with reading entrypoint for runtime-sklearn", ioe);
        }

        List<String> args = new ArrayList<>(List.of("start", "/shared/"));

        CorePort servicePort = new CorePort(HTTP_PORT, HTTP_PORT);
        CorePort grpcPort = new CorePort(GRPC_PORT, GRPC_PORT);

        String img = StringUtils.hasText(functionSpec.getImage()) ? functionSpec.getImage() : image;

        //validate image
        if (img == null || !img.startsWith(SklearnServeRuntime.IMAGE)) {
            throw new IllegalArgumentException("invalid or empty image, must be based on " + SklearnServeRuntime.IMAGE);
        }

        //build runnable
        K8sRunnable k8sServeRunnable = K8sServeRunnable
            .builder()
            .runtime(SklearnServeRuntime.RUNTIME)
            .task(SklearnServeTaskSpec.KIND)
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
            .secrets(coreSecrets)
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

    /**
     * Retrieves the path of the model file (either .pkl or .joblib) inside the model.
     *
     * @param model the model
     * @return the path of the model file
     * @throws CoreRuntimeException if the model files are not present or the model file is not found
     */
    private String getFilePath(Model model) {
        TypeReference<List<FileInfo>> typeRef = new TypeReference<List<FileInfo>>(){};
        List<FileInfo> files = JacksonMapper.CUSTOM_OBJECT_MAPPER.convertValue(model.getStatus().get("files"), typeRef);
        if (files == null || files.isEmpty()) {
            throw new CoreRuntimeException("model files not found");
        }
        FileInfo modelFile = files.stream().filter(f -> f.getName().matches(".*\\.pkl$|.*\\.joblib$")).findFirst().orElse(null);
        if (modelFile == null) {
            throw new CoreRuntimeException("model file not found");
        }
        String path = (String)model.getSpec().get("path");
        if (!path.endsWith("/")) {
            path += "/";
        }
        path += modelFile.getPath();
        return path;
    }
}
