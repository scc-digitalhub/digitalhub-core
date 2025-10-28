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

package it.smartcommunitylabdhub.runtime.vllm;

import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.accessors.fields.KeyAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.model.Model;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.services.FunctionManager;
import it.smartcommunitylabdhub.commons.services.ModelManager;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CorePort;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sServeRunnable;
import it.smartcommunitylabdhub.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.relationships.RelationshipName;
import it.smartcommunitylabdhub.relationships.RelationshipsMetadata;
import it.smartcommunitylabdhub.runtime.vllm.models.VLLMAdapter;
import it.smartcommunitylabdhub.runtime.vllm.specs.VLLMServeFunctionSpec;
import it.smartcommunitylabdhub.runtime.vllm.specs.VLLMServeRunSpec;
import it.smartcommunitylabdhub.runtime.vllm.specs.VLLMServeTaskSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

public class VLLMServeRunner {

    private static final int HTTP_PORT = 8000;
    private static final int UID = 1000;
    private static final int GID = 1000;

    private final String image;
    private final int userId;
    private final int groupId;
    private final String otelEndpoint;
    private final VLLMServeFunctionSpec functionSpec;
    private final Map<String, String> secretData;

    private final K8sBuilderHelper k8sBuilderHelper;
    private final ModelManager modelService;
    private final FunctionManager functionService;

    public VLLMServeRunner(
        String image,
        Integer userId,
        Integer groupId,
        String otelEndpoint,
        VLLMServeFunctionSpec functionSpec,
        Map<String, String> secretData,
        K8sBuilderHelper k8sBuilderHelper,
        ModelManager modelService,
        FunctionManager functionService
    ) {
        this.image = image;
        this.functionSpec = functionSpec;
        this.secretData = secretData;
        this.k8sBuilderHelper = k8sBuilderHelper;
        this.modelService = modelService;
        this.functionService = functionService;

        this.userId = userId != null ? userId : UID;
        this.groupId = groupId != null ? groupId : GID;
        this.otelEndpoint = otelEndpoint;
    }

    public K8sRunnable produce(Run run) {
        VLLMServeRunSpec runSpec = VLLMServeRunSpec.with(run.getSpec());
        VLLMServeTaskSpec taskSpec = runSpec.getTaskServeSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());

        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
        );

        List<CoreEnv> coreSecrets = secretData == null
            ? null
            : secretData.entrySet().stream().map(e -> new CoreEnv(e.getKey(), e.getValue())).toList();

        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

        //read source and build context
        List<ContextRef> contextRefs = null;
        String path = functionSpec.getUrl();
        if (path.startsWith(Keys.STORE_PREFIX)) {
            path = linkToModel(run, path);
        }

        UriComponents uri = UriComponentsBuilder.fromUriString(path).build();

        List<String> args = new ArrayList<>(
            List.of(
                "vllm",
                "serve"
            )
        );

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
            args.add("--model");
            args.add(mdlId);
            if (revision != null) {
                args.add("--revision");
                args.add(revision);
            }
            defaultServedModelName = mdlId;
        } else {
            args.add("--model");
            args.add("/shared/model");

            contextRefs =
                Collections.singletonList(
                    ContextRef.builder().source(path).protocol(uri.getScheme()).destination("model").build()
                );
        }

        Map<String, List<String>> defaultArgMap = new HashMap<>();
        defaultArgMap.put("--host", List.of("0.0.0.0"));
        defaultArgMap.put("--port", List.of(String.valueOf(HTTP_PORT)));
        defaultArgMap.put("--served-model-name", List.of(functionSpec.getModelName() != null ? functionSpec.getModelName() : defaultServedModelName));

        if (otelEndpoint != null && !otelEndpoint.isBlank() && Boolean.TRUE.equals(runSpec.getEnableTelemetry())) {
            defaultArgMap.put("--otlp-traces-endpoint", List.of(otelEndpoint));
            defaultArgMap.put("--collect-detailed-traces", List.of("all"));
        }

        if (runSpec.getArgs() != null && runSpec.getArgs().size() > 0) {
            mergeArgs(defaultArgMap, args);
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
                args.add(adapter.getName() +  "=" + ref);
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

        String img = StringUtils.hasText(functionSpec.getImage()) ? functionSpec.getImage() : image;

        //validate image
        if (img == null || !img.startsWith(VLLMServeRuntime.IMAGE)) {
            throw new IllegalArgumentException(
                "invalid or empty image, must be based on " + VLLMServeRuntime.IMAGE
            );
        }

        //build runnable
        K8sRunnable k8sServeRunnable = K8sServeRunnable
            .builder()
            .runtime(VLLMServeRuntime.RUNTIME)
            .task(VLLMServeTaskSpec.KIND)
            .state(State.READY.name())
            .labels(
                k8sBuilderHelper != null
                    ? List.of(new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction()))
                    : null
            )
            //base
            .image(img)
            .command("vllm")
            .args(args.toArray(new String[0]))
            .contextRefs(contextRefs)
            .envs(coreEnvList)
            .secrets(coreSecrets)
            .resources(k8sBuilderHelper != null ? k8sBuilderHelper.convertResources(taskSpec.getResources()) : null)
            .volumes(taskSpec.getVolumes())
            .nodeSelector(taskSpec.getNodeSelector())
            .affinity(taskSpec.getAffinity())
            .tolerations(taskSpec.getTolerations())
            .runtimeClass(taskSpec.getRuntimeClass())
            .priorityClass(taskSpec.getPriorityClass())
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

    private String linkToModel(Run run, String path) {
        KeyAccessor keyAccessor = KeyAccessor.with(path);
        if (!EntityName.MODEL.getValue().equals(keyAccessor.getType())) {
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
        for (int  i = 0; i < explicitArgs.size(); i++) {
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
