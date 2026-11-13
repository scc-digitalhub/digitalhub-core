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

package it.smartcommunitylabdhub.runtime.sglang;

import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.accessors.fields.KeyAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.files.models.PathAccessor;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sLabelHelper;
import it.smartcommunitylabdhub.framework.k8s.objects.AppProtocol;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CorePort;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreResource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sServeRunnable;
import it.smartcommunitylabdhub.functions.FunctionManager;
import it.smartcommunitylabdhub.models.Model;
import it.smartcommunitylabdhub.models.ModelManager;
import it.smartcommunitylabdhub.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.relationships.RelationshipName;
import it.smartcommunitylabdhub.relationships.RelationshipsMetadata;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.modelserve.models.LoRAAdapter;
import it.smartcommunitylabdhub.runtime.modelserve.models.OpenAIService;
import it.smartcommunitylabdhub.runtime.modelserve.specs.ModelServeRunStatus;
import it.smartcommunitylabdhub.runtime.sglang.config.SGLangProperties;
import it.smartcommunitylabdhub.runtime.sglang.specs.SGLangServeFunctionSpec;
import it.smartcommunitylabdhub.runtime.sglang.specs.SGLangServeRunSpec;
import it.smartcommunitylabdhub.runtime.sglang.specs.SGLangServeTaskSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

public class SGLangServeRunner {

    public static final int HTTP_PORT = 30000;
    public static final int UID = 1000;
    public static final int GID = 1000;
    private static final String DEFAULT_MEM_SIZE = "16Gi";
    private static final String DEFAULT_VOLUME_SIZE = "10Gi";

    private static final String HOME_DIR = "/shared";
    private final String image;

    private final int userId;
    private final int groupId;
    private final String homeDir;
    private final String volumeSizeSpec;
    private final String memSizeSpec;

    private final Map<String, String> secretData;

    private final K8sBuilderHelper k8sBuilderHelper;
    private final K8sLabelHelper k8sLabelHelper;

    private final ModelManager modelService;
    private final FunctionManager functionService;

    public SGLangServeRunner(
        SGLangProperties properties,
        Map<String, String> secretData,
        K8sBuilderHelper k8sBuilderHelper,
        K8sLabelHelper k8sLabelHelper,
        ModelManager modelService,
        FunctionManager functionService
    ) {
        Assert.notNull(properties, "properties are required");
        Assert.hasText(properties.getImage(), "image can not be null");
        this.image = properties.getImage();

        this.userId = properties.getUserId() != null ? properties.getUserId() : UID;
        this.groupId = properties.getGroupId() != null ? properties.getGroupId() : GID;
        this.homeDir = properties.getHomeDir() != null ? properties.getHomeDir() : HOME_DIR;
        this.volumeSizeSpec = properties.getVolumeSize() != null ? properties.getVolumeSize() : DEFAULT_VOLUME_SIZE;
        this.memSizeSpec = properties.getMemSize() != null ? properties.getMemSize() : DEFAULT_MEM_SIZE;

        this.secretData = secretData;
        this.k8sBuilderHelper = k8sBuilderHelper;
        this.k8sLabelHelper = k8sLabelHelper;
        this.modelService = modelService;
        this.functionService = functionService;
    }

    public K8sRunnable produce(Run run) {
        SGLangServeRunSpec runSpec = SGLangServeRunSpec.with(run.getSpec());
        SGLangServeTaskSpec taskSpec = runSpec.getTaskServeSpec();
        SGLangServeFunctionSpec functionSpec = runSpec.getFunctionSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());

        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
        );

        List<CoreEnv> coreSecrets =
            secretData == null
                ? null
                : secretData
                      .entrySet()
                      .stream()
                      .map(e -> new CoreEnv(e.getKey(), e.getValue()))
                      .toList();

        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

        List<CoreVolume> coreVolumes = new ArrayList<>(
            taskSpec.getVolumes() != null ? taskSpec.getVolumes() : List.of()
        );

        //resources for execution
        CoreResource resources = taskSpec.getResources() != null ? taskSpec.getResources() : new CoreResource();
        String memSize = resources.getMem() != null ? resources.getMem() : memSizeSpec;
        resources.setMem(memSize);

        CoreResource diskResource = new CoreResource();
        String volumeSize = resources.getDisk() != null ? resources.getDisk() : volumeSizeSpec;
        diskResource.setDisk(volumeSize);
        Optional.ofNullable(k8sBuilderHelper).ifPresent(helper -> {
            Optional.ofNullable(helper.buildSharedVolume(diskResource)).ifPresent(coreVolumes::add);
        });

        //path is in run spec after build
        String path = runSpec.getPath();
        if (!StringUtils.hasText(path)) {
            throw new IllegalArgumentException("model path is missing or invalid");
        }
        if (!path.startsWith(Keys.STORE_PREFIX)) {
            //enforce model from store
            throw new IllegalArgumentException("invalid model path, must be a store reference");
        }

        //resolve model
        Model model = resolveModel(path);
        if (model == null) {
            throw new IllegalArgumentException("model not found for path: " + path);
        }

        String modelUri = resolveModelUri(model);

        //resolve adapters
        Map<String, String> adapterUris = new HashMap<>();
        if (functionSpec.getAdapters() != null) {
            for (LoRAAdapter adapter : functionSpec.getAdapters()) {
                //resolve adapter URI or any other necessary information here
                Model adapterModel = resolveModel(adapter.getUrl());
                if (adapterModel == null) {
                    throw new IllegalArgumentException("adapter model not found for path: " + adapter.getUrl());
                }
                String adapterUri = resolveModelUri(adapterModel);
                adapterUris.put(adapter.getName(), adapterUri);
            }
        }

        //set lineage
        RelationshipsMetadata relMetadata = RelationshipsMetadata.from(run.getMetadata());
        List<RelationshipDetail> relationships = new ArrayList<>();
        Optional.ofNullable(relMetadata.getRelationships()).ifPresent(relationships::addAll);

        RelationshipDetail rel = new RelationshipDetail();
        rel.setType(RelationshipName.CONSUMES);
        rel.setSource(run.getKey());
        rel.setDest(model.getKey());
        relationships.add(rel);
        relMetadata.setRelationships(relationships);
        run.setMetadata(MapUtils.mergeMultipleMaps(run.getMetadata(), relMetadata.toMap()));

        String modelName = K8sBuilderHelper.sanitizeNames(
            StringUtils.hasText(runSpec.getModelName()) ? runSpec.getModelName() : model.getName()
        );

        //build openai info now
        ModelServeRunStatus status = ModelServeRunStatus.with(run.getStatus());
        OpenAIService openai = new OpenAIService();
        openai.setModel(modelName);
        openai.setModelUrl(modelUri);

        if (adapterUris != null && !adapterUris.isEmpty()) {
            openai.setAdapters(
                adapterUris
                    .entrySet()
                    .stream()
                    .map(e -> LoRAAdapter.builder().name(e.getKey()).url(e.getValue()).build())
                    .toList()
            );
        }

        //set features and persist
        openai.setEngine(SGLangServeRuntime.ENGINE);
        openai.setFeatures(new LinkedList<>(SGLangServeRuntime.OPENAI_FEATURES.keySet()));
        status.setOpenai(openai);
        run.setStatus(MapUtils.mergeMultipleMaps(run.getStatus(), status.toMap()));

        //build args and ENV
        coreEnvList.add(new CoreEnv("SGLANG_HOST_IP", "0.0.0.0"));
        coreEnvList.add(new CoreEnv("SGLANG_PORT", String.valueOf(HTTP_PORT)));
        coreEnvList.add(new CoreEnv("SGLANG_CACHE_DIR", homeDir + "/.sglang_cache"));
        coreEnvList.add(new CoreEnv("HOME", homeDir));
        coreEnvList.add(new CoreEnv("HF_HOME", homeDir + "/.hf_cache"));

        //the entrypoint expects the model URI as the first argument
        List<String> args = new ArrayList<>();
        args.add("serve");
        args.add(modelUri);
        args.add("--served-model-name");
        args.add(modelName);
        if (adapterUris != null && !adapterUris.isEmpty()) {
            args.add("--enable-lora");
            args.add("--lora-paths");
            adapterUris
                .entrySet()
                .forEach(e -> {
                    args.add(e.getKey() + "=" + e.getValue());
                });
        }

        CorePort servicePort = new CorePort(HTTP_PORT, HTTP_PORT, AppProtocol.openai);

        //evaluate service names
        List<String> serviceNames = new ArrayList<>();
        if (taskSpec.getServiceName() != null && StringUtils.hasText(taskSpec.getServiceName())) {
            //prepend with function name
            serviceNames.add(taskAccessor.getFunction() + "-" + taskSpec.getServiceName());
        }

        if (functionService != null) {
            //check if latest
            Function latest = functionService.getLatestFunctionByName(run.getProject(), taskAccessor.getFunction());
            if (taskAccessor.getFunctionId().equals(latest.getId())) {
                //prepend with function name
                serviceNames.add(taskAccessor.getFunction() + "-latest");
            }
        }

        String img = StringUtils.hasText(functionSpec.getImage()) ? functionSpec.getImage() : image;

        //validate image
        if (img == null || !img.startsWith(SGLangServeRuntime.IMAGE)) {
            throw new IllegalArgumentException("invalid or empty image, must be based on " + SGLangServeRuntime.IMAGE);
        }

        //build runnable
        K8sRunnable k8sServeRunnable = K8sServeRunnable.builder()
            .runtime(SGLangServeRuntime.RUNTIME)
            .task(SGLangServeTaskSpec.KIND)
            .state(State.READY.name())
            .labels(
                k8sLabelHelper != null
                    ? List.of(new CoreLabel(k8sLabelHelper.buildCoreLabel("function"), taskAccessor.getFunction()))
                    : null
            )
            //base
            .image(img)
            .command("/opt/sglang/bin/sglang")
            .args(args.toArray(new String[0]))
            // .contextSources(contextSources)
            // .contextRefs(contextRefs)
            .envs(coreEnvList)
            .secrets(coreSecrets)
            .resources(k8sBuilderHelper != null ? k8sBuilderHelper.convertResources(resources) : null)
            .volumes(coreVolumes)
            .template(taskSpec.getProfile())
            //specific
            .replicas(taskSpec.getReplicas())
            .servicePorts(List.of(servicePort))
            .serviceType(taskSpec.getServiceType())
            .serviceNames(serviceNames != null && !serviceNames.isEmpty() ? serviceNames : null)
            //fixed securityContext
            .fsGroup(groupId)
            .runAsGroup(groupId)
            .runAsUser(userId)
            .build();

        k8sServeRunnable.setId(run.getId());
        k8sServeRunnable.setProject(run.getProject());

        return k8sServeRunnable;
    }

    private Model resolveModel(String path) {
        KeyAccessor keyAccessor = KeyAccessor.with(path);
        if (!EntityUtils.getEntityName(Model.class).equalsIgnoreCase(keyAccessor.getType())) {
            throw new CoreRuntimeException("invalid entity kind reference, expected model");
        }
        try {
            Model model =
                keyAccessor.getId() != null
                    ? modelService.findModel(keyAccessor.getId())
                    : modelService.getLatestModelByKey(keyAccessor.getProject(), path);
            if (model == null) {
                throw new CoreRuntimeException("invalid entity reference, model not found");
            }

            return model;
        } catch (NoSuchElementException e) {
            throw new CoreRuntimeException("invalid entity reference, model not found", e);
        }
    }

    private String resolveModelUri(Model model) {
        //model actual path is in the model spec
        PathAccessor pathAccessor = PathAccessor.with(model.getSpec());
        String modelPath = pathAccessor.getPath();

        UriComponents uri = UriComponentsBuilder.fromUriString(modelPath).build();

        //let's make sure we have a valid model uri
        if (uri.getScheme() == null || !StringUtils.hasText(uri.getScheme())) {
            throw new IllegalArgumentException("invalid model url, missing scheme");
        }
        if (uri.getHost() == null || !StringUtils.hasText(uri.getHost())) {
            throw new IllegalArgumentException("invalid model url");
        }

        //sanitize path
        String modelUri = null;
        if (uri.getScheme().equals("s3://")) {
            //s3 is supported
            modelUri = modelPath.trim();
        }
        if (uri.getScheme().equals("hf") || uri.getScheme().equals("huggingface")) {
            //huggingface is supported via direct model ref
            modelUri = modelPath.substring(uri.getScheme().length() + 3).trim();
        }

        if (!StringUtils.hasText(modelUri)) {
            throw new IllegalArgumentException("invalid model, unable to determine model URI");
        }
        return modelUri;
    }
}
