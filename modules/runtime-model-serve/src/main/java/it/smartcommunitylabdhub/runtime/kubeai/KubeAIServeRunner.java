package it.smartcommunitylabdhub.runtime.kubeai;

import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.accessors.fields.KeyAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.enums.RelationshipName;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.metadata.RelationshipsMetadata;
import it.smartcommunitylabdhub.commons.models.model.Model;
import it.smartcommunitylabdhub.commons.models.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.services.ModelService;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import it.smartcommunitylabdhub.runtime.kubeai.models.KubeAIFeature;
import it.smartcommunitylabdhub.runtime.kubeai.models.KubeAIModelSpec;
import it.smartcommunitylabdhub.runtime.kubeai.specs.KubeAIServeFunctionSpec;
import it.smartcommunitylabdhub.runtime.kubeai.specs.KubeAIServeRunSpec;
import it.smartcommunitylabdhub.runtime.kubeai.specs.KubeAIServeTaskSpec;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KubeAIServeRunner {

    private static final String BASE_PROFILE = "cpu:1";

    private final KubeAIServeFunctionSpec functionSpec;

    private final ModelService modelService;
    private final K8sBuilderHelper k8sBuilderHelper;    
    private Map<String, String> secretData;
    private Map<String, String> credentialsData;

    private static final String KUBEAI_API_GROUP = "kubeai.org";
    private static final String KUBEAI_API_VERSION = "v1";
    private static final String KUBEAI_API_KIND = "Model";
    private static final String KUBEAI_API_PLURAL = "models";

    public KubeAIServeRunner(
        KubeAIServeFunctionSpec functionSpec,
        Map<String, String> secretData, 
        Map<String, String> credentialsData, 
        K8sBuilderHelper k8sBuilderHelper,
        ModelService modelService
    ) {
        this.functionSpec = functionSpec;
        this.modelService = modelService;
        this.secretData = secretData;
        this.credentialsData = credentialsData;
        this.k8sBuilderHelper = k8sBuilderHelper;
    }

    @SuppressWarnings("unchecked")
    public K8sCRRunnable produce(Run run) {
        KubeAIServeRunSpec runSpec = KubeAIServeRunSpec.with(run.getSpec());
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(runSpec.getTaskServeSpec().toMap());

        String url = functionSpec.getUrl();
        if (url.startsWith(Keys.STORE_PREFIX)) {
            KeyAccessor keyAccessor = KeyAccessor.with(url);
            if (!EntityName.MODEL.getValue().equals(keyAccessor.getType())) {
                throw new CoreRuntimeException("invalid entity kind reference, expected model");
            }
            Model model = keyAccessor.getId() != null
                ? modelService.findModel(keyAccessor.getId())
                : modelService.getLatestModel(keyAccessor.getProject(), keyAccessor.getName());
            if (model == null) {
                throw new CoreRuntimeException("invalid entity reference, model not found");
            }
            if (!model.getKind().equals("huggingface")) {
                throw new CoreRuntimeException("invalid entity reference, expected Hugginface model");
            }
            RelationshipDetail rel = new RelationshipDetail();
            rel.setType(RelationshipName.CONSUMES);
            rel.setDest(run.getKey());
            rel.setSource(model.getKey());
            RelationshipsMetadata relationships = RelationshipsMetadata.from(run.getMetadata());
            relationships.getRelationships().add(rel);
            run.getMetadata().putAll(relationships.toMap());

            url = (String) model.getSpec().get("path");
            if (!url.endsWith("/")) {
                url += "/";
            }
        }

        // populate env from edplicit env, then from user secret and then from explicit project secret 
        Map<String, String> env = new HashMap<>();
        // credentials secrets
        if (credentialsData != null) {
            env.putAll(credentialsData);
        }
        // explcit secrets
        if (secretData != null) {
            env.putAll(secretData);
        }

        // enviornment variables from run spec
        if (runSpec.getEnv() != null) {
            env.putAll(runSpec.getEnv());
        }

        // set to 1 if no scaling is defined
        // int replicas = 1;
        // if (runSpec.getScaling() != null) {
        //     if (runSpec.getScaling().getReplicas() != null) {
        //         replicas = runSpec.getScaling().getReplicas();
        //     } else if (runSpec.getScaling().getMinReplicas() != null) {
        //         replicas = runSpec.getScaling().getMinReplicas();
        //     }            
        // }
        // int minReplicas = runSpec.getScaling() != null && runSpec.getScaling().getMinReplicas() != null ? runSpec.getScaling().getMinReplicas() : replicas;
        String resourceProfile = runSpec.getProfile() != null ? runSpec.getProfile() : BASE_PROFILE;

        KubeAIModelSpec modelSpec = KubeAIModelSpec.builder()
            .url(url)
            .image(functionSpec.getImage())
            .args(runSpec.getArgs())
            .cacheProfile(runSpec.getCacheProfile())
            .resourceProfile(resourceProfile)
            .adapters(functionSpec.getAdapters())
            .features(
                functionSpec.getFeatures() == null ? Collections.emptyList() :
                functionSpec.getFeatures().stream().map(KubeAIFeature::name).toList()
            )            
            .engine(functionSpec.getEngine().name())
            .env(env)
            .files(runSpec.getFiles())
            .replicas(runSpec.getScaling().getReplicas())
            .minReplicas(runSpec.getScaling().getMinReplicas())
            .maxReplicas(runSpec.getScaling().getMaxReplicas())
            .autoscalingDisabled(runSpec.getScaling().getAutoscalingDisabled())
            .targetRequests(runSpec.getScaling().getTargetRequests())
            .scaleDownDelaySeconds(runSpec.getScaling().getScaleDownDelaySeconds())
            .loadBalancing(runSpec.getScaling().getLoadBalancing())
        .build();

        K8sCRRunnable k8sRunnable = K8sCRRunnable.builder()
        .runtime(KubeAIServeRuntime.RUNTIME)
        .task(KubeAIServeTaskSpec.KIND)
        .state(State.READY.name())
        .labels(
            k8sBuilderHelper != null
                ? List.of(new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction()))
                : null
        )
        .name(functionSpec.getModelName())
        .apiGroup(KUBEAI_API_GROUP)
        .apiVersion(KUBEAI_API_VERSION)
        .kind(KUBEAI_API_KIND)
        .plural(KUBEAI_API_PLURAL)
        .spec(JacksonMapper.CUSTOM_OBJECT_MAPPER.convertValue(modelSpec, Map.class))
        .build();


        k8sRunnable.setId(run.getId());
        k8sRunnable.setProject(run.getProject());

        return k8sRunnable;
    }
}
