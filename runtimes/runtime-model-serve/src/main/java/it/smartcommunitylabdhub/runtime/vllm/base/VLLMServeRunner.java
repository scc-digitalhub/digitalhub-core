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

package it.smartcommunitylabdhub.runtime.vllm.base;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import io.kubernetes.client.custom.Quantity;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.accessors.fields.KeyAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
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
import it.smartcommunitylabdhub.runtime.vllm.base.models.VLLMAdapter;
import it.smartcommunitylabdhub.runtime.vllm.base.specs.VLLMServeFunctionSpec;
import it.smartcommunitylabdhub.runtime.vllm.base.specs.VLLMServeRunSpec;
import it.smartcommunitylabdhub.runtime.vllm.base.specs.VLLMServeTaskSpec;
import it.smartcommunitylabdhub.runtime.vllm.config.VLLMProperties;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
public class VLLMServeRunner {

    private static final int HTTP_PORT = 8000;
    private static final int UID = 1000;
    private static final int GID = 100;
    private static final String DEFAULT_MEM_SIZE = "16Gi";
    private static final String DEFAULT_VOLUME_SIZE = "10Gi";

    private static final String HOME_DIR = "/shared";
    private final Resource passwdTemplate = new ClassPathResource("runtime-vllm/passwd.template");
    private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();
    private final String passwdFile;

    private final String runtime;
    private final String image;
    private final String volumeSizeSpec;
    private final String memSizeSpec;
    private final int userId;
    private final int groupId;
    private final String homeDir;
    private final String otelEndpoint;
    private final VLLMServeFunctionSpec functionSpec;
    private final Map<String, String> secretData;

    private final K8sBuilderHelper k8sBuilderHelper;
    private final ModelManager modelService;
    private final FunctionManager functionService;

    public VLLMServeRunner(
        String runtime,
        String image,
        VLLMProperties properties,
        VLLMServeFunctionSpec functionSpec,
        Map<String, String> secretData,
        K8sBuilderHelper k8sBuilderHelper,
        ModelManager modelService,
        FunctionManager functionService
    ) {
        Assert.notNull(properties, "properties are required");
        Assert.hasText(image, "image can not be null");

        this.runtime = runtime;
        this.image = image;

        this.functionSpec = functionSpec;
        this.secretData = secretData;
        this.k8sBuilderHelper = k8sBuilderHelper;
        this.modelService = modelService;
        this.functionService = functionService;

        this.userId = properties.getUserId() != null ? properties.getUserId() : UID;
        this.groupId = properties.getGroupId() != null ? properties.getGroupId() : GID;
        this.homeDir = properties.getHomeDir() != null ? properties.getHomeDir() : HOME_DIR;
        this.volumeSizeSpec = properties.getVolumeSize() != null ? properties.getVolumeSize() : DEFAULT_VOLUME_SIZE;
        this.memSizeSpec = properties.getMemSize() != null ? properties.getMemSize() : DEFAULT_MEM_SIZE;

        this.otelEndpoint = properties.getLlmOtelEndpoint();

        String passwd = null;
        try {
            log.debug("Buuilding passwd template for {}:{} with home {}", this.userId, this.groupId, this.homeDir);
            Mustache template = mustacheFactory.compile(
                new InputStreamReader(passwdTemplate.getInputStream()),
                "passwd"
            );

            passwd =
                template
                    .execute(
                        new StringWriter(),
                        Map.of("userId", this.userId, "groupId", this.groupId, "homeDir", this.homeDir)
                    )
                    .toString();
        } catch (IOException ioe) {
            log.error("error with building passwd template for runtime-vllm", ioe);
            //disable template
        }

        this.passwdFile = passwd;
    }

    public K8sRunnable produce(Run run) {
        VLLMServeRunSpec runSpec = VLLMServeRunSpec.with(run.getSpec());
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(run.getSpec());
        VLLMServeTaskSpec taskSpec = VLLMServeTaskSpec.with(run.getSpec());

        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
        );
        // workaround for mc config dir to avoid eventual user permissions issues
        coreEnvList.add(new CoreEnv("MC_CONFIG_DIR", "/shared/mc"));

        List<CoreEnv> coreSecrets = secretData == null
            ? null
            : secretData.entrySet().stream().map(e -> new CoreEnv(e.getKey(), e.getValue())).toList();

        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

        List<CoreVolume> coreVolumes = new ArrayList<>(
            taskSpec.getVolumes() != null ? taskSpec.getVolumes() : List.of()
        );

        //define resources: we need RAM size to evaluate cache if cpu is used, otherwise VLLM will take 50% of the whole node RAM as cache
        CoreResource resources = taskSpec.getResources() != null ? taskSpec.getResources() : new CoreResource();
        String memSize = resources != null && resources.getMem() != null ? resources.getMem() : memSizeSpec;

        if (Boolean.TRUE.equals(runSpec.getUseCpuImage())) {
            coreEnvList.add(new CoreEnv("VLLM_BACKEND", "cpu"));

            // define VLLM_CPU_KVCACHE_SPACE as 50% of mem size if not defined, to avoid using too much memory for caching and OOM
            Integer cacheSize = calculateCacheSize(memSize);
            Optional
                .ofNullable(cacheSize)
                .ifPresent(size -> coreEnvList.add(new CoreEnv("VLLM_CPU_KVCACHE_SPACE", String.valueOf(size))));
        }

        //check if scratch disk is requested as resource or set default
        String volumeSize = resources != null && resources.getDisk() != null ? resources.getDisk() : volumeSizeSpec;
        CoreResource diskResource = new CoreResource();
        diskResource.setDisk(volumeSize);

        Optional
            .ofNullable(k8sBuilderHelper)
            .ifPresent(helper -> {
                Optional.ofNullable(helper.buildSharedVolume(diskResource)).ifPresent(coreVolumes::add);
            });

        //read source and build context
        List<ContextRef> contextRefs = null;
        List<ContextSource> contextSources = new ArrayList<>();

        //inject custom passwd to add our user
        if (passwdFile != null) {
            ContextSource entry = ContextSource
                .builder()
                .name("passwd")
                .base64(Base64.getEncoder().encodeToString(passwdFile.getBytes(StandardCharsets.UTF_8)))
                .mountPath("/etc/passwd")
                .build();
            contextSources.add(entry);
        }

        //url is in run spec after build
        String url = runSpec.getUrl();
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("model url is missing or invalid");
        }

        if (url.startsWith(Keys.STORE_PREFIX)) {
            url = linkToModel(run, url);
        }

        UriComponents uri = UriComponentsBuilder.fromUriString(url).build();

        List<String> args = new ArrayList<>();

        String defaultServedModelName = "model";
        // model dir or model id
        if ("huggingface".equals(uri.getScheme()) || "hf".equals(uri.getScheme())) {
            String mdlId = uri.getHost() + uri.getPath();
            String revision = null;
            if (mdlId.contains(":")) {
                String[] parts = mdlId.split(":");
                mdlId = parts[0];
                revision = parts[1];
            }
            args.add(mdlId);
            if (revision != null) {
                args.add("--revision");
                args.add(revision);
            }
            defaultServedModelName = mdlId;
        } else {
            args.add("/shared/model");

            contextRefs =
                Collections.singletonList(
                    ContextRef.builder().source(url).protocol(uri.getScheme()).destination("model").build()
                );
        }

        Map<String, List<String>> defaultArgMap = new HashMap<>();
        defaultArgMap.put("--host", List.of("0.0.0.0"));
        defaultArgMap.put("--port", List.of(String.valueOf(HTTP_PORT)));
        defaultArgMap.put(
            "--served-model-name",
            List.of(functionSpec.getModelName() != null ? functionSpec.getModelName() : defaultServedModelName)
        );

        //enable caching by default
        defaultArgMap.put("--enable-prefix-caching", List.of());

        if (otelEndpoint != null && !otelEndpoint.isBlank() && Boolean.TRUE.equals(runSpec.getEnableTelemetry())) {
            defaultArgMap.put("--otlp-traces-endpoint", List.of(otelEndpoint));
            defaultArgMap.put("--collect-detailed-traces", List.of("all"));
        }

        if (runSpec.getArgs() != null && runSpec.getArgs().size() > 0) {
            mergeArgs(defaultArgMap, runSpec.getArgs());
        }

        for (Map.Entry<String, List<String>> arg : defaultArgMap.entrySet()) {
            args.add(arg.getKey());
            if (!arg.getValue().isEmpty()) args.addAll(arg.getValue());
        }

        if (functionSpec.getAdapters() != null && functionSpec.getAdapters().size() > 0) {
            contextRefs = new LinkedList<>(contextRefs);
            args.add("--enable-lora");
            args.add("--lora-modules");

            for (VLLMAdapter adapter : functionSpec.getAdapters()) {
                String adapterPath = adapter.getUrl();
                if (adapterPath.startsWith(Keys.STORE_PREFIX)) {
                    adapterPath = linkToModel(run, adapterPath);
                }

                UriComponents adapterUri = UriComponentsBuilder.fromUriString(adapterPath).build();
                String adapterSource = adapter.getUrl().trim();
                String ref = adapterSource;

                if (!"huggingface".equals(adapterUri.getScheme()) || "hf".equals(adapterUri.getScheme())) {
                    if (!adapterSource.endsWith("/")) adapterSource += "/";
                    ref = "/shared/adapters/" + adapter.getName() + "/";
                    contextRefs =
                        Collections.singletonList(
                            ContextRef
                                .builder()
                                .source(adapterSource)
                                .protocol(adapterUri.getScheme())
                                .destination("adapters/" + adapter.getName())
                                .build()
                        );
                }
                args.add(adapter.getName() + "=" + ref);
            }
        }

        CorePort servicePort = new CorePort(HTTP_PORT, HTTP_PORT);

        //evaluate service names
        List<String> serviceNames = new ArrayList<>();
        if (taskSpec.getServiceName() != null && StringUtils.hasText(taskSpec.getServiceName())) {
            //prepend with function name
            serviceNames.add(taskAccessor.getFunction() + "-" + taskSpec.getServiceName());
        }

        if (functionService != null) {
            //check if latest
            Function latest = functionService.getLatestFunction(run.getProject(), taskAccessor.getFunction());
            if (taskAccessor.getFunctionId().equals(latest.getId())) {
                //prepend with function name
                serviceNames.add(taskAccessor.getFunction() + "-latest");
            }
        }

        //validate image
        // if (img == null || !img.startsWith(VLLMServeRuntime.IMAGE)) {
        //     throw new IllegalArgumentException(
        //         "invalid or empty image, must be based on " + VLLMServeRuntime.IMAGE
        //     );
        // }

        appendHFVariables(coreEnvList);

        //build runnable
        K8sRunnable k8sServeRunnable = K8sServeRunnable
            .builder()
            .runtime(runtime)
            .task(runtime + "+serve")
            .state(State.READY.name())
            .labels(
                k8sBuilderHelper != null
                    ? List.of(new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction()))
                    : null
            )
            //base
            .image(image)
            .command("python3", "-m", "vllm.entrypoints.openai.api_server")
            .args(args)
            .contextRefs(contextRefs)
            .contextSources(contextSources)
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

    private Integer calculateCacheSize(String memSize) {
        if (memSize == null || memSize.isEmpty()) {
            return 1; // Default 1Gi
        }

        // Calculate 50% and convert to GiB
        Quantity quantity = Quantity.fromString(memSize);
        BigDecimal value = quantity
            .getNumber()
            .multiply(new BigDecimal("0.5"))
            .setScale(0, RoundingMode.UP)
            .divide(new BigDecimal(1024L * 1024L * 1024L), 0, RoundingMode.DOWN);

        return value.intValue();
    }

    private void appendHFVariables(List<CoreEnv> coreEnvList) {
        if (coreEnvList.stream().noneMatch(ce -> ce.name().equals("HF_HOME"))) {
            coreEnvList.add(new CoreEnv("HF_HOME", "/shared/huggingface"));
        }
        // if (coreEnvList.stream().noneMatch(ce -> ce.name().equals("TRANSFORMERS_CACHE"))) {
        //     coreEnvList.add(new CoreEnv("TRANSFORMERS_CACHE", "/shared/huggingface"));
        // }
    }

    private String linkToModel(Run run, String path) {
        KeyAccessor keyAccessor = KeyAccessor.with(path);
        if (!EntityUtils.getEntityName(Model.class).equalsIgnoreCase(keyAccessor.getType())) {
            throw new CoreRuntimeException("invalid entity kind reference, expected model");
        }
        Model model = keyAccessor.getId() != null
            ? modelService.findModel(keyAccessor.getId())
            : modelService.getLatestModel(keyAccessor.getProject(), keyAccessor.getName());
        if (model == null) {
            throw new CoreRuntimeException("invalid entity reference, HuggingFace model not found");
        }
        if (!model.getKind().equals("huggingface") && !model.getKind().equals("hf")) {
            throw new CoreRuntimeException("invalid entity reference, expected HuggingFace model");
        }
        RelationshipDetail rel = new RelationshipDetail();
        rel.setType(RelationshipName.CONSUMES);
        rel.setDest(run.getKey());
        rel.setSource(model.getKey());
        RelationshipsMetadata relationships = RelationshipsMetadata.from(run.getMetadata());
        relationships.getRelationships().add(rel);
        run.getMetadata().putAll(relationships.toMap());
        path = (String) model.getSpec().get("path");
        return path;
    }

    private void mergeArgs(Map<String, List<String>> extraArgMap, List<String> explicitArgs) {
        // merge explicit args into args, if not exists

        String key = null;
        List<String> values = new LinkedList<>();
        for (int i = 0; i < explicitArgs.size(); i++) {
            if (explicitArgs.get(i).startsWith("--")) {
                //store previous
                if (key != null) {
                    if (!extraArgMap.containsKey(key)) {
                        extraArgMap.put(key, values);
                    }
                }
                //new key
                key = explicitArgs.get(i);
                values = new LinkedList<>();
            } else {
                //value for current key
                values.add(explicitArgs.get(i));
            }
        }
        if (key != null) {
            if (!extraArgMap.containsKey(key)) {
                extraArgMap.put(key, values);
            }
        }
    }
}
