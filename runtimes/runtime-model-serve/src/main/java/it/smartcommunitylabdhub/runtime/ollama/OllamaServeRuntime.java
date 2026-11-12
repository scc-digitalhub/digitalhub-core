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

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.CredentialsService;
import it.smartcommunitylabdhub.authorization.utils.UserAuthenticationHelper;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.commons.infrastructure.Configuration;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.models.task.TaskBaseSpec;
import it.smartcommunitylabdhub.commons.services.ConfigurationService;
import it.smartcommunitylabdhub.commons.services.SecretService;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionBaseRuntime;
import it.smartcommunitylabdhub.framework.k8s.model.K8sServiceDetails;
import it.smartcommunitylabdhub.framework.k8s.model.K8sServiceInfo;
import it.smartcommunitylabdhub.framework.k8s.objects.AppProtocol;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.functions.FunctionManager;
import it.smartcommunitylabdhub.models.ModelManager;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runs.lifecycle.RunState;
import it.smartcommunitylabdhub.runtime.modelserve.models.OpenAIService;
import it.smartcommunitylabdhub.runtime.modelserve.specs.ModelServeRunStatus;
import it.smartcommunitylabdhub.runtime.ollama.config.OllamaProperties;
import it.smartcommunitylabdhub.runtime.ollama.specs.OllamaServeFunctionSpec;
import it.smartcommunitylabdhub.runtime.ollama.specs.OllamaServeRunSpec;
import it.smartcommunitylabdhub.runtime.ollama.specs.OllamaServeTaskSpec;
import it.smartcommunitylabdhub.runtime.vllm.base.VLLMServeRunner;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
@RuntimeComponent(runtime = OllamaServeRuntime.RUNTIME)
public class OllamaServeRuntime
    extends K8sFunctionBaseRuntime<OllamaServeFunctionSpec, OllamaServeRunSpec, ModelServeRunStatus, K8sRunnable>
    implements InitializingBean
{

    public static final String ENGINE = "ollama";
    public static final String RUNTIME = "ollamaserve";
    public static final String IMAGE = "ollama/ollama";
    public static final Map<String, String> OPENAI_FEATURES = new LinkedHashMap<>();

    static {
        OPENAI_FEATURES.put("Chat Completions API", "/v1/chat/completions");
        OPENAI_FEATURES.put("Completions API", "/v1/completions");
        OPENAI_FEATURES.put("Embeddings API", "/v1/embeddings");
        OPENAI_FEATURES.put("Responses API", "/v1/responses");
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

    @Autowired
    protected OllamaProperties properties;

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.hasText(properties.getImage(), "image can not be null or empty");
        Assert.isTrue(properties.getImage().startsWith(IMAGE), "image must be a version of " + IMAGE);
    }

    @Override
    public OllamaServeRunSpec build(@NotNull Function function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!OllamaServeRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind %s unsupported, expecting %s".formatted(
                    String.valueOf(run.getKind()),
                    OllamaServeRunSpec.KIND
                )
            );
        }

        OllamaServeFunctionSpec funSpec = OllamaServeFunctionSpec.with(function.getSpec());
        OllamaServeRunSpec runSpec = OllamaServeRunSpec.with(run.getSpec());

        String kind = task.getKind();

        //build task spec as defined
        TaskBaseSpec taskSpec = switch (kind) {
            case OllamaServeTaskSpec.KIND -> OllamaServeTaskSpec.with(task.getSpec());
            default -> throw new IllegalArgumentException(
                "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
            );
        };

        //path is defined in function spec but overridable in run spec
        String path = funSpec.getPath();
        if (!StringUtils.hasText(path)) {
            throw new IllegalArgumentException("model path is missing or invalid");
        }
        if (StringUtils.hasText(runSpec.getPath())) {
            //path must begin with function spec path, we allow tags etc as suffixes
            if (!runSpec.getPath().toLowerCase().startsWith(path.toLowerCase())) {
                throw new IllegalArgumentException("invalid path override, must be subpath of function path");
            }

            path = runSpec.getPath();
        }
        runSpec.setPath(path);

        // //adapters are defined in function spec but overridable in run spec
        // List<LoRAAdapter> adapters = funSpec.getAdapters();
        // if (runSpec.getAdapters() != null && !runSpec.getAdapters().isEmpty()) {
        //     adapters = runSpec.getAdapters();
        // }
        // runSpec.setAdapters(adapters);

        //build run merging task spec overrides
        Map<String, Serializable> map = new HashMap<>();
        map.putAll(runSpec.toMap());
        taskSpec.toMap().forEach(map::putIfAbsent);

        OllamaServeRunSpec serveSpec = OllamaServeRunSpec.with(map);
        //ensure function is not modified
        serveSpec.setFunctionSpec(funSpec);

        return serveSpec;
    }

    @Override
    public K8sRunnable run(@NotNull Run run) {
        //check run kind
        if (!OllamaServeRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind %s unsupported, expecting %s".formatted(
                    String.valueOf(run.getKind()),
                    OllamaServeRunSpec.KIND
                )
            );
        }

        OllamaServeRunSpec runSpec = OllamaServeRunSpec.with(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        K8sRunnable runnable = switch (runAccessor.getTask()) {
            case OllamaServeTaskSpec.KIND -> new OllamaServeRunner(
                properties,
                secretService.getSecretData(run.getProject(), runSpec.getTaskServeSpec().getSecrets()),
                k8sBuilderHelper,
                k8sLabelHelper,
                modelService,
                functionService
            ).produce(run);
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
    public ModelServeRunStatus onRunning(@NotNull Run run, RunRunnable runnable) {
        ModelServeRunStatus status = ModelServeRunStatus.with(run.getStatus());
        OllamaServeFunctionSpec functionSpec = OllamaServeFunctionSpec.with(run.getSpec());
        OllamaServeRunSpec runSpec = OllamaServeRunSpec.with(run.getSpec());

        if (status == null || functionSpec == null || runSpec == null) {
            return null;
        }

        //we need to patch openai info with the correct service url, available only after creating the service
        if (
            status.getService() != null &&
            status.getService().getUrl() != null &&
            status.getOpenai() != null &&
            status.getOpenai().getBaseUrl() == null
        ) {
            K8sServiceInfo service = status.getService();
            String baseUrl = service.getUrl();

            Set<K8sServiceDetails> urls = new HashSet<>();
            if (service.getUrls() != null) {
                urls.addAll(service.getUrls());
            }

            // Add v1 url as generic service
            urls.add(new K8sServiceDetails(baseUrl + "/v1", VLLMServeRunner.HTTP_PORT, AppProtocol.openai.name()));

            service.setUrls(new ArrayList<>(urls));
            status.setService(service);

            OpenAIService openai = status.getOpenai();
            openai.setBaseUrl(baseUrl + "/v1");
            status.setOpenai(openai);
        }

        status.setState(RunState.RUNNING.name());
        status.setMessage("Model %s ready".formatted(runSpec.getModelName()));
        return status;
    }

    @Override
    public boolean isSupported(@NotNull Run run) {
        return OllamaServeRunSpec.KIND.equals(run.getKind());
    }
}
