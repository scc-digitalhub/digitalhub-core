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
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CorePort;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreServiceType;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sServeRunnable;
import it.smartcommunitylabdhub.runtime.kubeai.models.KubeAIModelSpec;
import it.smartcommunitylabdhub.runtime.kubeai.specs.KubeAIServeFunctionSpec;
import it.smartcommunitylabdhub.runtime.kubeai.specs.KubeAIServeRunSpec;
import it.smartcommunitylabdhub.runtime.kubeai.specs.KubeAIServeTaskSpec;
import it.smartcommunitylabdhub.runtime.modelserve.specs.ModelServeServeTaskSpec;
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

public class KubeAIServeRunner {

    private final KubeAIServeFunctionSpec functionSpec;

    private final K8sBuilderHelper k8sBuilderHelper;
    private final ModelService modelService;

    private static final String KUBEAI_API_GROUP = "";
    private static final String KUBEAI_API_VERSION = "v1";
    private static final String KUBEAI_API_KIND = "Model";
    private static final String KUBEAI_API_PLURAL = "models";

    public KubeAIServeRunner(
        KubeAIServeFunctionSpec functionSpec,
        K8sBuilderHelper k8sBuilderHelper,
        ModelService modelService
    ) {
        this.functionSpec = functionSpec;
        this.k8sBuilderHelper = k8sBuilderHelper;
        this.modelService = modelService;
    }

    public K8sCRRunnable produce(Run run) {
        KubeAIServeRunSpec runSpec = KubeAIServeRunSpec.with(run.getSpec());
        KubeAIServeTaskSpec taskSpec = runSpec.getTaskServeSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());

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
        .apiGroup(KUBEAI_API_GROUP)
        .apiVersion(KUBEAI_API_VERSION)
        .kind(KUBEAI_API_KIND)
        .plural(KUBEAI_API_PLURAL)
        .build();


        k8sRunnable.setId(run.getId());
        k8sRunnable.setProject(run.getProject());

        return k8sRunnable;
    }
}
