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

import it.smartcommunitylabdhub.authorization.services.CredentialsService;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.services.ConfigurationService;
import it.smartcommunitylabdhub.commons.services.FunctionManager;
import it.smartcommunitylabdhub.commons.services.ModelManager;
import it.smartcommunitylabdhub.commons.services.SecretService;
import it.smartcommunitylabdhub.framework.k8s.base.K8sBaseRuntime;
import it.smartcommunitylabdhub.framework.k8s.model.K8sServiceInfo;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runtime.vllm.base.models.OpenAIService;
import it.smartcommunitylabdhub.runtime.vllm.base.models.VLLMAdapter;
import it.smartcommunitylabdhub.runtime.vllm.base.specs.VLLMServeFunctionSpec;
import it.smartcommunitylabdhub.runtime.vllm.base.specs.VLLMServeRunSpec;
import it.smartcommunitylabdhub.runtime.vllm.base.specs.VLLMServeRunStatus;
import it.smartcommunitylabdhub.runtime.vllm.text.specs.VLLMServeTextFunctionSpec;
import it.smartcommunitylabdhub.runtimes.lifecycle.RunState;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;

@Slf4j
public abstract class VLLMServeRuntime<F extends VLLMServeFunctionSpec, R extends VLLMServeRunSpec>
    extends K8sBaseRuntime<F, R, VLLMServeRunStatus, K8sRunnable>
    implements InitializingBean {

    public static final String IMAGE = "vllm/vllm-openai";

    protected static final String VLLM_ENGINE = "VLLM";

    @Autowired
    protected SecretService secretService;

    @Autowired
    protected ModelManager modelService;

    @Autowired
    protected FunctionManager functionService;

    @Autowired
    protected CredentialsService credentialsService;

    @Autowired
    protected ConfigurationService configurationService;

    @Value("${runtime.vllmserve.image}")
    protected String image;

    @Value("${runtime.vllmserve.cpu-image}")
    protected String cpuImage;

    @Value("${runtime.vllmserve.volume-size-spec:10Gi}")
    protected String volumeSizeSpec;

    @Value("${runtime.vllmserve.user-id}")
    protected Integer userId;

    @Value("${runtime.vllmserve.group-id}")
    protected Integer groupId;

    @Value("${runtime.vllmserve.llm.otel-endpoint:}")
    protected String otelEndpoint;

    public VLLMServeRuntime() {}

    public abstract Map<String, String> getOpenAIFeatures();

    public abstract Map<String, String> getExtraFeatures();

    @Override
    public VLLMServeRunStatus onRunning(@NotNull Run run, RunRunnable runnable) {
        VLLMServeRunStatus status = VLLMServeRunStatus.with(run.getStatus());
        VLLMServeTextFunctionSpec functionSpec = VLLMServeTextFunctionSpec.with(run.getSpec());
        if (status == null || functionSpec == null) {
            return null;
        }

        if (status.getService() != null && status.getService().getUrl() != null && status.getOpenai() == null) {
            K8sServiceInfo service = status.getService();
            String baseUrl = service.getUrl();

            Set<String> urls = new HashSet<>();
            if (service.getUrls() != null) {
                urls.addAll(service.getUrls());
            }
            OpenAIService openai = new OpenAIService();
            openai.setBaseUrl(baseUrl + "/v1");
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
            openai.setFeatures(new LinkedList<>(getOpenAIFeatures().keySet()));
            status.setOpenai(openai);
            // TODO check
            getOpenAIFeatures().values().forEach(url -> urls.add(baseUrl + url));
            getExtraFeatures().values().forEach(url -> urls.add(baseUrl + url));
            urls.add(baseUrl + "/v1/models");
            service.setUrls(new ArrayList<>(urls));
            status.setService(service);
        }

        status.setState(RunState.RUNNING.name());
        status.setMessage("Model %s ready".formatted(functionSpec.getModelName()));
        return status;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.hasText(image, "image can not be null or empty");
        // Assert.isTrue(image.startsWith(IMAGE), "image must be a version of " + IMAGE);
    }
}
