package it.smartcommunitylabdhub.runtime.kubeai;

import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.accessors.fields.KeyAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.enums.RelationshipName;
import it.smartcommunitylabdhub.commons.models.metadata.RelationshipsMetadata;
import it.smartcommunitylabdhub.commons.models.model.Model;
import it.smartcommunitylabdhub.commons.models.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.services.ModelService;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import it.smartcommunitylabdhub.runtime.kubeai.models.KubeAIModelSpec;
import it.smartcommunitylabdhub.runtime.kubeai.specs.KubeAIServeFunctionSpec;
import it.smartcommunitylabdhub.runtime.kubeai.specs.KubeAIServeRunSpec;
import java.util.Map;

public class KubeAIServeRunner {

    private final KubeAIServeFunctionSpec functionSpec;

    private final ModelService modelService;

    private static final String KUBEAI_API_GROUP = "kubeai.org";
    private static final String KUBEAI_API_VERSION = "v1";
    private static final String KUBEAI_API_KIND = "Model";
    private static final String KUBEAI_API_PLURAL = "models";

    public KubeAIServeRunner(
        KubeAIServeFunctionSpec functionSpec,
        ModelService modelService
    ) {
        this.functionSpec = functionSpec;
        this.modelService = modelService;
    }

    @SuppressWarnings("unchecked")
    public K8sCRRunnable produce(Run run) {
        KubeAIServeRunSpec runSpec = KubeAIServeRunSpec.with(run.getSpec());

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

        KubeAIModelSpec modelSpec = KubeAIModelSpec.builder()
            .url(url)
            .image(functionSpec.getImage())
            .args(runSpec.getArgs())
            .cacheProfile(runSpec.getCacheProfile())
            .resourceProfile(runSpec.getResourceProfile())
            .adapters(functionSpec.getAdapters())
            .features(functionSpec.getFeatures())
            .engine(functionSpec.getEngine())
            .files(runSpec.getFiles())
            .env(runSpec.getEnv())
            .replicas(runSpec.getReplicas())
            .minReplicas(runSpec.getMinReplicas())
            .maxReplicas(runSpec.getMaxReplicas())
            .autoscalingDisabled(runSpec.getAutoscalingDisabled())
            .targetRequests(runSpec.getTargetRequests())
            .scaleDownDelaySeconds(runSpec.getScaleDownDelaySeconds())
            .loadBalancing(runSpec.getLoadBalancing())
        .build();

        K8sCRRunnable k8sRunnable = K8sCRRunnable.builder()
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
