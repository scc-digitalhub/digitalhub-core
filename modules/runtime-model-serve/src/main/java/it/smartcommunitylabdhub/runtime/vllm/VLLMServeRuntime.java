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

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.CredentialsService;
import it.smartcommunitylabdhub.authorization.utils.UserAuthenticationHelper;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.commons.infrastructure.Configuration;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.models.base.Executable;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.models.task.TaskBaseSpec;
import it.smartcommunitylabdhub.commons.services.ConfigurationService;
import it.smartcommunitylabdhub.commons.services.FunctionManager;
import it.smartcommunitylabdhub.commons.services.ModelManager;
import it.smartcommunitylabdhub.commons.services.SecretService;
import it.smartcommunitylabdhub.framework.k8s.base.K8sBaseRuntime;
import it.smartcommunitylabdhub.framework.k8s.model.K8sServiceInfo;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runtime.vllm.models.OpenAIService;
import it.smartcommunitylabdhub.runtime.vllm.models.VLLMAdapter;
import it.smartcommunitylabdhub.runtime.vllm.specs.VLLMServeFunctionSpec;
import it.smartcommunitylabdhub.runtime.vllm.specs.VLLMServeRunSpec;
import it.smartcommunitylabdhub.runtime.vllm.specs.VLLMServeRunStatus;
import it.smartcommunitylabdhub.runtime.vllm.specs.VLLMServeTaskSpec;
import it.smartcommunitylabdhub.runtimes.lifecycle.RunState;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;

@Slf4j
@RuntimeComponent(runtime = VLLMServeRuntime.RUNTIME)
public class VLLMServeRuntime
    extends K8sBaseRuntime<VLLMServeFunctionSpec, VLLMServeRunSpec, VLLMServeRunStatus, K8sRunnable>
    implements InitializingBean {

    public static final String RUNTIME = "vllmserve";
    public static final String IMAGE = "vllm/vllm-openai";
    
    private static final String VLLM_ENGINE = "VLLM";
    private static final Map<String, String> features = new LinkedHashMap<>();
    static {
        features.put("Completions API", "/v1/completions");
        features.put("Chat Completions API", "/v1/chat/completions");
        features.put("Embeddings API", "/v1/embeddings");
        features.put("Transcriptions API", "/v1/audio/transcriptions");
        features.put("Translations API", "/v1/audio/translations");
        features.put("Custom Tokenizer API", "/tokenize");
        features.put("Custom Detokenizer API", "/detokenize");
        features.put("Custom Pooling API", "/pooling");
        features.put("Custom Classification API", "/classify");
        features.put("Custom Score API", "/score");
        features.put("Custom Re-rank API", "/rerank");
        features.put("Jina AI / Cohere v1 Re-rank API", "/v1/rerank");
        features.put("Cohere v2 re-rank API", "/v2/rerank");
    }

    @Autowired
    private SecretService secretService;

    @Autowired
    private ModelManager modelService;

    @Autowired
    private FunctionManager functionService;

    @Autowired
    private CredentialsService credentialsService;

    @Autowired
    private ConfigurationService configurationService;

    @Value("${runtime.vllmserve.image}")
    private String image;

    @Value("${runtime.vllmserve.user-id}")
    private Integer userId;

    @Value("${runtime.vllmserve.group-id}")
    private Integer groupId;

    @Value("${llm.otel-endpoint:}")
    private String otelEndpoint;

    public VLLMServeRuntime() {}

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.hasText(image, "image can not be null or empty");
        Assert.isTrue(image.startsWith(IMAGE), "image must be a version of " + IMAGE);
    }

    @Override
    public VLLMServeRunSpec build(@NotNull Executable function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!VLLMServeRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(
                        String.valueOf(run.getKind()),
                        VLLMServeRunSpec.KIND
                    )
            );
        }

        VLLMServeFunctionSpec funSpec = VLLMServeFunctionSpec.with(function.getSpec());
        VLLMServeRunSpec runSpec = VLLMServeRunSpec.with(run.getSpec());

        String kind = task.getKind();

        //build task spec as defined
        TaskBaseSpec taskSpec =
            switch (kind) {
                case VLLMServeTaskSpec.KIND -> {
                    yield VLLMServeTaskSpec.with(task.getSpec());
                }
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build run merging task spec overrides
        Map<String, Serializable> map = new HashMap<>();
        map.putAll(runSpec.toMap());
        taskSpec.toMap().forEach(map::putIfAbsent);

        VLLMServeRunSpec serveSpec = VLLMServeRunSpec.with(map);
        //ensure function is not modified
        serveSpec.setFunctionSpec(funSpec);

        return serveSpec;
    }

    @Override
    public K8sRunnable run(@NotNull Run run) {
        //check run kind
        if (!VLLMServeRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(
                        String.valueOf(run.getKind()),
                        VLLMServeRunSpec.KIND
                    )
            );
        }

        VLLMServeRunSpec runSpec = VLLMServeRunSpec.with(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        K8sRunnable runnable =
            switch (runAccessor.getTask()) {
                case VLLMServeTaskSpec.KIND -> new VLLMServeRunner(
                    image,
                    userId,
                    groupId,
                    otelEndpoint,
                    runSpec.getFunctionSpec(),
                    secretService.getSecretData(run.getProject(), runSpec.getTaskServeSpec().getSecrets()),
                    k8sBuilderHelper,
                    modelService,
                    functionService
                )
                    .produce(run);
                default -> throw new IllegalArgumentException("Kind not recognized. Cannot retrieve the right Runner");
            };

        //extract auth from security context to inflate secured credentials
        UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
        if (auth != null) {
            //get credentials from providers
            List<Credentials> credentials = credentialsService.getCredentials((UserAuthentication<?>) auth);
            runnable.setCredentials(credentials);
        }

        //inject configuration
        List<Configuration> configurations = configurationService.getConfigurations();
        runnable.setConfigurations(configurations);

        return runnable;
    }

    @Override
    public VLLMServeRunStatus onRunning(@NotNull Run run, RunRunnable runnable) {
        VLLMServeRunStatus status = VLLMServeRunStatus.with(run.getStatus());
        VLLMServeFunctionSpec functionSpec = VLLMServeFunctionSpec.with(run.getSpec());
        if (status == null || functionSpec == null) {
            return null;
        }

        if (status.getService() != null && status.getService().getUrl() != null && !status.getService().getUrl().endsWith("/v1")) {
            K8sServiceInfo service = status.getService();
            String baseUrl = service.getUrl() + "/v1";
            service.setUrl(baseUrl);

            Set<String> urls = new HashSet<>();
            if (service.getUrls() != null) {
                urls.addAll(service.getUrls());
            }

            //build openapi descriptor only once
            if (status.getOpenai() == null) {            
                OpenAIService openai = new OpenAIService();
                openai.setBaseUrl(baseUrl);
                openai.setModel(functionSpec.getModelName());
                openai.setModelUrl(functionSpec.getUrl());

                if (functionSpec.getAdapters() != null) {
                    openai.setAdapters(
                        functionSpec
                            .getAdapters()
                            .stream()
                            .map(a -> VLLMAdapter.builder().name(a.getName()).build())
                            .toList()
                    );
                }

                //set features and persist
                openai.setEngine(VLLM_ENGINE);
                openai.setFeatures(new LinkedList<>(features.keySet()));
                status.setOpenai(openai);
                // TODO check
                urls.add(baseUrl + "/models");
                urls.addAll(features.values());
                service.setUrls(new ArrayList<>(urls));
                status.setService(service);
            }
        }

        status.setState(RunState.RUNNING.name());
        status.setMessage("Model %s ready".formatted(functionSpec.getModelName()));
        return status;
    }
    @Override
    public boolean isSupported(@NotNull Run run) {
        return VLLMServeRunSpec.KIND.equals(run.getKind());
    }
}
