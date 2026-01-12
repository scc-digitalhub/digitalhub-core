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

package it.smartcommunitylabdhub.runtime.vllm.pooling;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.utils.UserAuthenticationHelper;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.commons.infrastructure.Configuration;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.models.task.TaskBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runtime.vllm.base.VLLMServeRunner;
import it.smartcommunitylabdhub.runtime.vllm.base.VLLMServeRuntime;
import it.smartcommunitylabdhub.runtime.vllm.pooling.specs.VLLMServePoolingFunctionSpec;
import it.smartcommunitylabdhub.runtime.vllm.pooling.specs.VLLMServePoolingRunSpec;
import it.smartcommunitylabdhub.runtime.vllm.pooling.specs.VLLMServePoolingServeTaskSpec;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RuntimeComponent(runtime = VLLMServePoolingRuntime.RUNTIME)
public class VLLMServePoolingRuntime extends VLLMServeRuntime<VLLMServePoolingFunctionSpec, VLLMServePoolingRunSpec> {

    public static final String RUNTIME = "vllmserve-pooling";

    private static final Map<String, String> openAIFeatures = new LinkedHashMap<>();
    private static final Map<String, String> features = new LinkedHashMap<>();

    static {
        openAIFeatures.put("Embeddings API", "/v1/embeddings");

        features.put("Custom Tokenizer API", "/tokenize");
        features.put("Custom Detokenizer API", "/detokenize");
        features.put("Custom Pooling API", "/pooling");
        features.put("Custom Classification API", "/classify");
        features.put("Custom Score API", "/score");
        features.put("Custom Re-rank API", "/rerank");
        features.put("Jina AI / Cohere v1 Re-rank API", "/v1/rerank");
        features.put("Cohere v2 re-rank API", "/v2/rerank");
    }

    public VLLMServePoolingRuntime() {}

    @Override
    public Map<String, String> getOpenAIFeatures() {
        return openAIFeatures;
    }

    @Override
    public Map<String, String> getExtraFeatures() {
        return features;
    }

    @Override
    public VLLMServePoolingRunSpec build(@NotNull Function function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!VLLMServePoolingRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(
                        String.valueOf(run.getKind()),
                        VLLMServePoolingRunSpec.KIND
                    )
            );
        }

        VLLMServePoolingFunctionSpec funSpec = VLLMServePoolingFunctionSpec.with(function.getSpec());
        VLLMServePoolingRunSpec runSpec = VLLMServePoolingRunSpec.with(run.getSpec());

        String kind = task.getKind();

        //build task spec as defined
        TaskBaseSpec taskSpec =
            switch (kind) {
                case VLLMServePoolingServeTaskSpec.KIND -> {
                    yield VLLMServePoolingServeTaskSpec.with(task.getSpec());
                }
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build run merging task spec overrides
        Map<String, Serializable> map = new HashMap<>();
        map.putAll(runSpec.toMap());
        taskSpec.toMap().forEach(map::putIfAbsent);

        VLLMServePoolingRunSpec serveSpec = VLLMServePoolingRunSpec.with(map);
        //ensure function is not modified
        serveSpec.setFunctionSpec(funSpec);

        return serveSpec;
    }

    @Override
    public K8sRunnable run(@NotNull Run run) {
        //check run kind
        if (!VLLMServePoolingRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(
                        String.valueOf(run.getKind()),
                        VLLMServePoolingRunSpec.KIND
                    )
            );
        }

        VLLMServePoolingRunSpec runSpec = VLLMServePoolingRunSpec.with(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        K8sRunnable runnable =
            switch (runAccessor.getTask()) {
                case VLLMServePoolingServeTaskSpec.KIND -> new VLLMServeRunner(
                    RUNTIME,
                    image,
                    cpuImage,
                    userId,
                    groupId,
                    volumeSizeSpec,
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
    public boolean isSupported(@NotNull Run run) {
        return VLLMServePoolingRunSpec.KIND.equals(run.getKind());
    }
}
