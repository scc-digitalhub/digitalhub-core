/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package it.smartcommunitylabdhub.runtime.kubeai.base;

import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.accessors.fields.KeyAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.model.Model;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.services.ModelManager;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sSecretHelper;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import it.smartcommunitylabdhub.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.relationships.RelationshipName;
import it.smartcommunitylabdhub.relationships.RelationshipsMetadata;
import it.smartcommunitylabdhub.runtime.kubeai.models.KubeAIEngine;
import it.smartcommunitylabdhub.runtime.kubeai.models.KubeAIModelSpec;
import it.smartcommunitylabdhub.runtime.kubeai.models.KubeAiEnvFrom;
import it.smartcommunitylabdhub.runtime.kubeai.models.KubeAiEnvFromRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

public class KubeAIServeRunner {

    private static final String BASE_PROFILE = "cpu";

    private final KubeAIServeFunctionSpec functionSpec;

    private final String runtime;
    private final String engine;
    private final List<String> features;

    private final ModelManager modelService;
    private final K8sBuilderHelper k8sBuilderHelper;
    private final K8sSecretHelper k8sSecretHelper;
    private Map<String, String> secretData;

    private static final String KUBEAI_API_GROUP = "kubeai.org";
    private static final String KUBEAI_API_VERSION = "v1";
    private static final String KUBEAI_API_KIND = "Model";
    private static final String KUBEAI_API_PLURAL = "models";

    public KubeAIServeRunner(
        String runtime,
        String engine,
        List<String> features,
        KubeAIServeFunctionSpec functionSpec,
        Map<String, String> secretData,
        K8sBuilderHelper k8sBuilderHelper,
        K8sSecretHelper k8sSecretHelper,
        ModelManager modelService
    ) {
        this.runtime = runtime;
        this.engine = engine;
        this.features = features;
        this.functionSpec = functionSpec;
        this.modelService = modelService;
        this.secretData = secretData;
        this.k8sBuilderHelper = k8sBuilderHelper;
        this.k8sSecretHelper = k8sSecretHelper;
    }

    @SuppressWarnings("unchecked")
    public K8sCRRunnable produce(Run run) {
        KubeAIServeRunSpec runSpec = KubeAIServeRunSpec.with(run.getSpec());
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(run.getSpec());
        KubeAIServeTaskSpec taskSpec = KubeAIServeTaskSpec.with(run.getSpec());

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

        List<CoreEnv> coreSecrets = secretData == null
            ? null
            : secretData.entrySet().stream().map(e -> new CoreEnv(e.getKey(), e.getValue())).toList();

        // populate env from explicit env only, secrets are referenced in the spec
        Map<String, String> env = new HashMap<>();

        // environment variables from run spec
        if (runSpec.getEnv() != null) {
            //TODO evaluate enforcing uppercase
            env.putAll(
                runSpec.getEnv().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
            );
        }

        List<KubeAiEnvFrom> envFrom = null;
        String secretName = k8sSecretHelper.getSecretName(runtime, runtime + "+serve", run.getId());
        if (secretName != null) {
            //TODO evaluate if we will get the secret, for now we assume it is always there

            envFrom =
                Collections.singletonList(KubeAiEnvFrom.builder().secretRef(new KubeAiEnvFromRef(secretName)).build());
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
        // int minReplicas = runSpec.getScaling() != null && runSpec.getScaling().getMinReplicas() != null
        //     ? runSpec.getScaling().getMinReplicas()
        //     : replicas;
        int processors = runSpec.getProcessors() != null ? runSpec.getProcessors() : 1;
        String resourceProfile = StringUtils.hasText(taskSpec.getProfile()) ? taskSpec.getProfile() : BASE_PROFILE;

        //build custom resource name matching model name
        //TODO evaluate letting users specify real names
        String modelName = functionSpec.getModelName() != null
            ? K8sBuilderHelper.sanitizeNames(functionSpec.getModelName() + "-" + run.getId())
            : run.getId();

        //enforce kubeAI max model name length 40chars
        if (modelName.length() > 39) {
            modelName = modelName.substring(0, 39);
        }

        List<String> args = new ArrayList<>();
        if (KubeAIEngine.VLLM.name().equals(engine)) {
            //inject args to reduce logging
            args.add("--disable-log-requests");
            args.add("--disable-log-stats");
            args.add("--uvicorn-log-level=warning");
        }
        if (KubeAIEngine.OLlama.name().equals(engine)) {
            //inject args to reduce logging
            env.put("OLLAMA_DEBUG", "false");
            env.put("GIN_MODE", "release");
        }

        if (runSpec.getArgs() != null) {
            args.addAll(runSpec.getArgs());
        }

        KubeAIModelSpec modelSpec = KubeAIModelSpec
            .builder()
            .url(url)
            .image(functionSpec.getImage())
            .args(args.isEmpty() ? null : args)
            .cacheProfile(runSpec.getCacheProfile())
            .resourceProfile(resourceProfile + ":" + Integer.toString(processors))
            .adapters(functionSpec.getAdapters())
            .features(features == null ? Collections.emptyList() : features)
            .engine(engine)
            .env(env)
            .envFrom(envFrom)
            .files(runSpec.getFiles())
            .replicas(runSpec.getScaling().getReplicas())
            .minReplicas(runSpec.getScaling().getMinReplicas())
            .maxReplicas(runSpec.getScaling().getMaxReplicas())
            .autoscalingDisabled(runSpec.getScaling().getAutoscalingDisabled())
            .targetRequests(runSpec.getScaling().getTargetRequests())
            .scaleDownDelaySeconds(runSpec.getScaling().getScaleDownDelaySeconds())
            .loadBalancing(runSpec.getScaling().getLoadBalancing())
            .build();

        K8sCRRunnable k8sRunnable = K8sCRRunnable
            .builder()
            .runtime(runtime)
            .task(runtime + "+serve")
            .state(State.READY.name())
            .labels(
                k8sBuilderHelper != null
                    ? List.of(new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction()))
                    : null
            )
            .name(modelName)
            .apiGroup(KUBEAI_API_GROUP)
            .apiVersion(KUBEAI_API_VERSION)
            .kind(KUBEAI_API_KIND)
            .plural(KUBEAI_API_PLURAL)
            .spec(JacksonMapper.CUSTOM_OBJECT_MAPPER.convertValue(modelSpec, Map.class))
            .requiresSecret(envFrom != null)
            .build();

        //inject secrets
        k8sRunnable.setSecrets(coreSecrets);

        k8sRunnable.setId(run.getId());
        k8sRunnable.setProject(run.getProject());

        return k8sRunnable;
    }
}
