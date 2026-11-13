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

package it.smartcommunitylabdhub.runtime.ollama;

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
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
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
import it.smartcommunitylabdhub.runtime.huggingface.specs.HuggingfaceModelSpec;
import it.smartcommunitylabdhub.runtime.modelserve.models.OpenAIService;
import it.smartcommunitylabdhub.runtime.modelserve.specs.ModelServeRunStatus;
import it.smartcommunitylabdhub.runtime.ollama.config.OllamaProperties;
import it.smartcommunitylabdhub.runtime.ollama.specs.OllamaModelSpec;
import it.smartcommunitylabdhub.runtime.ollama.specs.OllamaServeFunctionSpec;
import it.smartcommunitylabdhub.runtime.ollama.specs.OllamaServeRunSpec;
import it.smartcommunitylabdhub.runtime.ollama.specs.OllamaServeTaskSpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

public class OllamaServeRunner {

    public static final int HTTP_PORT = 11434;
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

    private final DefaultResourceLoader loader = new DefaultResourceLoader();
    private String entrypoint;

    public OllamaServeRunner(
        OllamaProperties properties,
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

        //load default entrypoint
        setEntrypoint(loader.getResource("classpath:/runtime-ollama/entrypoint.sh"));
    }

    public void setEntrypoint(Resource resource) {
        try {
            this.entrypoint = new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8);
        } catch (IOException ioe) {
            throw new CoreRuntimeException("error with reading entrypoint for runtime");
        }
    }

    public K8sRunnable produce(Run run) {
        OllamaServeRunSpec runSpec = OllamaServeRunSpec.with(run.getSpec());
        OllamaServeTaskSpec taskSpec = runSpec.getTaskServeSpec();
        OllamaServeFunctionSpec functionSpec = runSpec.getFunctionSpec();
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

        //TODO derive OLLAMA_KV_CACHE_TYPE from memory and let user override

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

        //for now we support only ollama and huggingface
        if (!OllamaModelSpec.KIND.equals(model.getKind()) && !HuggingfaceModelSpec.KIND.equals(model.getKind())) {
            throw new IllegalArgumentException("unsupported model kind: " + model.getKind());
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
        if (uri.getScheme().equals("ollama")) {
            modelUri = modelPath.substring("ollama://".length()).trim();
        }
        if (uri.getScheme().equals("hf") || uri.getScheme().equals("huggingface")) {
            modelUri = "hf.co/" + modelPath.substring(uri.getScheme().length() + 3).trim();
        }

        if (!StringUtils.hasText(modelUri)) {
            throw new IllegalArgumentException("invalid model, unable to determine model URI");
        }

        String modelName = K8sBuilderHelper.sanitizeNames(
            StringUtils.hasText(runSpec.getModelName()) ? runSpec.getModelName() : model.getName()
        );

        //build openai info now
        ModelServeRunStatus status = ModelServeRunStatus.with(run.getStatus());
        OpenAIService openai = new OpenAIService();
        openai.setModel(modelName);
        openai.setModelUrl(modelUri);

        // if (functionSpec.getAdapters() != null) {
        //     openai.setAdapters(
        //         functionSpec
        //             .getAdapters()
        //             .stream()
        //             .map(a -> LoRAAdapter.builder().name(a.getName()).build())
        //             .toList()
        //     );
        // }

        //set features and persist
        openai.setEngine(OllamaServeRuntime.ENGINE);
        openai.setFeatures(new LinkedList<>(OllamaServeRuntime.OPENAI_FEATURES.keySet()));
        status.setOpenai(openai);
        run.setStatus(MapUtils.mergeMultipleMaps(run.getStatus(), status.toMap()));

        //build context to inject entrypoint
        ContextSource entry = ContextSource.builder()
            .name("entrypoint.sh")
            .base64(Base64.getEncoder().encodeToString(entrypoint.getBytes(StandardCharsets.UTF_8)))
            .build();
        List<ContextSource> contextSources = List.of(entry);

        //build args and ENV
        coreEnvList.add(new CoreEnv("OLLAMA_HOST", "0.0.0.0:" + HTTP_PORT));
        coreEnvList.add(new CoreEnv("OLLAMA_ORIGINS", "*"));
        coreEnvList.add(new CoreEnv("OLLAMA_MODELS", homeDir));
        coreEnvList.add(new CoreEnv("OLLAMA_KEEP_ALIVE", "-1"));

        //the entrypoint expects the model URI as the first argument
        List<String> args = List.of(modelUri, modelName);

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
        if (img == null || !img.startsWith(OllamaServeRuntime.IMAGE)) {
            throw new IllegalArgumentException("invalid or empty image, must be based on " + OllamaServeRuntime.IMAGE);
        }

        //build runnable
        K8sRunnable k8sServeRunnable = K8sServeRunnable.builder()
            .runtime(OllamaServeRuntime.RUNTIME)
            .task(OllamaServeTaskSpec.KIND)
            .state(State.READY.name())
            .labels(
                k8sLabelHelper != null
                    ? List.of(new CoreLabel(k8sLabelHelper.buildCoreLabel("function"), taskAccessor.getFunction()))
                    : null
            )
            //base
            .image(img)
            .command(homeDir + "/entrypoint.sh")
            .args(args.toArray(new String[0]))
            .contextSources(contextSources)
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
}
