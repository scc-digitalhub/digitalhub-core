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

package it.smartcommunitylabdhub.runtime.vllm.speech;

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
import it.smartcommunitylabdhub.runtime.vllm.speech.specs.VLLMServeSpeechFunctionSpec;
import it.smartcommunitylabdhub.runtime.vllm.speech.specs.VLLMServeSpeechRunSpec;
import it.smartcommunitylabdhub.runtime.vllm.speech.specs.VLLMServeSpeechServeTaskSpec;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
@RuntimeComponent(runtime = VLLMServeSpeechRuntime.RUNTIME)
public class VLLMServeSpeechRuntime extends VLLMServeRuntime<VLLMServeSpeechFunctionSpec, VLLMServeSpeechRunSpec> {

    public static final String RUNTIME = "vllmserve-speech";

    private static final Map<String, String> features = new LinkedHashMap<>();
    private static final Map<String, String> openAIFeatures = new LinkedHashMap<>();

    static {
        openAIFeatures.put("Transcriptions API", "/v1/audio/transcriptions");
        openAIFeatures.put("Translations API", "/v1/audio/translations");

        features.put("Custom Tokenizer API", "/tokenize");
        features.put("Custom Detokenizer API", "/detokenize");
    }

    @Override
    public Map<String, String> getOpenAIFeatures() {
        return openAIFeatures;
    }

    @Override
    public Map<String, String> getExtraFeatures() {
        return features;
    }

    @Override
    public VLLMServeSpeechRunSpec build(@NotNull Function function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!VLLMServeSpeechRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                String.format(
                    "Run kind %s unsupported, expecting %s",
                    String.valueOf(run.getKind()),
                    VLLMServeSpeechRunSpec.KIND
                )
            );
        }

        VLLMServeSpeechFunctionSpec funSpec = VLLMServeSpeechFunctionSpec.with(function.getSpec());
        VLLMServeSpeechRunSpec runSpec = VLLMServeSpeechRunSpec.with(run.getSpec());

        String kind = task.getKind();

        //build task spec as defined
        TaskBaseSpec taskSpec =
            switch (kind) {
                case VLLMServeSpeechServeTaskSpec.KIND -> VLLMServeSpeechServeTaskSpec.with(task.getSpec());
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //url is defined in function spec but overridable in run spec
        String url = funSpec.getUrl();
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("model url is missing or invalid");
        }
        if (StringUtils.hasText(runSpec.getUrl())) {
            //url must begin with function spec url, we allow tags etc as suffixes
            if (!runSpec.getUrl().toLowerCase().startsWith(url.toLowerCase())) {
                throw new IllegalArgumentException("invalid url override, must be subpath of function url");
            }

            url = runSpec.getUrl();
        }
        runSpec.setUrl(url);

        //build run merging task spec overrides
        Map<String, Serializable> map = new HashMap<>();
        map.putAll(runSpec.toMap());
        taskSpec.toMap().forEach(map::putIfAbsent);

        VLLMServeSpeechRunSpec serveSpec = VLLMServeSpeechRunSpec.with(map);
        //ensure function is not modified
        serveSpec.setFunctionSpec(funSpec);

        return serveSpec;
    }

    @Override
    public K8sRunnable run(@NotNull Run run) {
        //check run kind
        if (!VLLMServeSpeechRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                String.format(
                    "Run kind %s unsupported, expecting %s",
                    String.valueOf(run.getKind()),
                    VLLMServeSpeechRunSpec.KIND
                )
            );
        }

        VLLMServeSpeechRunSpec runSpec = VLLMServeSpeechRunSpec.with(run.getSpec());

        String image = StringUtils.hasText(runSpec.getFunctionSpec().getImage())
            ? runSpec.getFunctionSpec().getImage()
            : Boolean.TRUE.equals(runSpec.getUseCpuImage())
                ? properties.getCpuImageAudio()
                : properties.getImageAudio();

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        K8sRunnable runnable =
            switch (runAccessor.getTask()) {
                case VLLMServeSpeechServeTaskSpec.KIND -> new VLLMServeRunner(
                    RUNTIME,
                    image,
                    properties,
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
            List<Credentials> credentials = credentialsService.getCredentials(auth);
            runnable.setCredentials(credentials);
        }

        //inject configuration
        List<Configuration> configurations = configurationService.getConfigurations();
        runnable.setConfigurations(configurations);

        return runnable;
    }

    @Override
    public boolean isSupported(@NotNull Run run) {
        return VLLMServeSpeechRunSpec.KIND.equals(run.getKind());
    }
}
