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

package it.smartcommunitylabdhub.runtime.kubeai.text;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
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
import it.smartcommunitylabdhub.files.provider.S3Credentials;
import it.smartcommunitylabdhub.framework.k8s.model.K8sServiceInfo;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import it.smartcommunitylabdhub.runtime.kubeai.base.KubeAIRuntime;
import it.smartcommunitylabdhub.runtime.kubeai.base.KubeAIServeRunStatus;
import it.smartcommunitylabdhub.runtime.kubeai.base.KubeAIServeRunner;
import it.smartcommunitylabdhub.runtime.kubeai.models.KubeAIEngine;
import it.smartcommunitylabdhub.runtime.kubeai.models.KubeAIFeature;
import it.smartcommunitylabdhub.runtime.kubeai.models.OpenAIService;
import it.smartcommunitylabdhub.runtime.kubeai.text.specs.KubeAITextFunctionSpec;
import it.smartcommunitylabdhub.runtime.kubeai.text.specs.KubeAITextRunSpec;
import it.smartcommunitylabdhub.runtime.kubeai.text.specs.KubeAITextServeTaskSpec;
import it.smartcommunitylabdhub.runtimes.lifecycle.RunState;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RuntimeComponent(runtime = KubeAITextRuntime.RUNTIME)
public class KubeAITextRuntime extends KubeAIRuntime<KubeAITextFunctionSpec, KubeAITextRunSpec> {

    public static final String RUNTIME = "kubeai-text";

    public KubeAITextRuntime() {}

    @Override
    public void afterPropertiesSet() throws Exception {
        // nothing to do
    }

    @Override
    public KubeAITextRunSpec build(@NotNull Executable function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!KubeAITextRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), KubeAITextRunSpec.KIND)
            );
        }

        KubeAITextFunctionSpec funSpec = KubeAITextFunctionSpec.with(function.getSpec());
        KubeAITextRunSpec runSpec = KubeAITextRunSpec.with(run.getSpec());

        String kind = task.getKind();

        //build task spec as defined
        TaskBaseSpec taskSpec =
            switch (kind) {
                case KubeAITextServeTaskSpec.KIND -> {
                    yield KubeAITextServeTaskSpec.with(task.getSpec());
                }
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build run merging task spec overrides
        Map<String, Serializable> map = new HashMap<>();
        map.putAll(runSpec.toMap());
        taskSpec.toMap().forEach(map::putIfAbsent);

        KubeAITextRunSpec serveSpec = KubeAITextRunSpec.with(map);

        //ensure function is not modified
        serveSpec.setFunctionSpec(funSpec);

        return serveSpec;
    }

    @Override
    public K8sCRRunnable run(@NotNull Run run) {
        //check run kind
        if (!KubeAITextRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), KubeAITextRunSpec.KIND)
            );
        }

        if (k8sBuilderHelper == null || k8sSecretHelper == null) {
            throw new IllegalArgumentException("k8s helpers not available");
        }

        KubeAITextRunSpec runSpec = KubeAITextRunSpec.with(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        K8sCRRunnable runnable =
            switch (runAccessor.getTask()) {
                case KubeAITextServeTaskSpec.KIND -> new KubeAIServeRunner(
                    KubeAITextRuntime.RUNTIME,
                    runSpec.getFunctionSpec().getEngine() != null
                        ? runSpec.getFunctionSpec().getEngine().name()
                        : KubeAIEngine.VLLM.name(),
                    runSpec.getFunctionSpec().getFeatures() != null
                        ? runSpec.getFunctionSpec().getFeatures().stream().map(KubeAIFeature::name).toList()
                        : Collections.emptyList(),
                    runSpec.getFunctionSpec(),
                    secretService.getSecretData(run.getProject(), runSpec.getSecrets()),
                    k8sBuilderHelper,
                    k8sSecretHelper,
                    modelService
                )
                    .produce(run);
                default -> throw new IllegalArgumentException("Kind not recognized. Cannot retrieve the right Runner");
            };

        //extract auth from security context to inflate secured credentials
        UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
        if (auth != null) {
            //get credentials from providers
            //keep only S3
            List<Credentials> credentials = credentialsService
                .getCredentials((UserAuthentication<?>) auth)
                .stream()
                .filter(s -> s instanceof S3Credentials)
                .toList();

            runnable.setCredentials(credentials);
        }

        //inject configuration
        List<Configuration> configurations = configurationService.getConfigurations();
        runnable.setConfigurations(configurations);

        return runnable;
    }

    @Override
    public KubeAIServeRunStatus onRunning(@NotNull Run run, RunRunnable runnable) {
        KubeAIServeRunStatus status = KubeAIServeRunStatus.with(run.getStatus());
        KubeAITextFunctionSpec functionSpec = KubeAITextFunctionSpec.with(run.getSpec());
        if (status == null || functionSpec == null) {
            return null;
        }

        //build openapi descriptor only once
        if (status.getOpenai() == null) {
            //inflate super or rebuild
            OpenAIService openai = Optional
                .ofNullable(super.onRunning(run, runnable))
                .map(s -> s.getOpenai())
                .orElse(new OpenAIService());

            //set features and persist
            openai.setEngine(functionSpec.getEngine() != null ? functionSpec.getEngine().name() : null);
            openai.setFeatures(functionSpec.getFeatures().stream().map(KubeAIFeature::name).toList());
            status.setOpenai(openai);
        }

        //build service descriptor only once
        if (status.getService() == null) {
            //inflate super or rebuild
            K8sServiceInfo service = Optional
                .ofNullable(super.onRunning(run, runnable))
                .map(s -> s.getService())
                .orElse(new K8sServiceInfo());

            //feature based urls
            String baseUrl = kubeAiEndpoint + "/openai";
            List<String> urls = service.getUrls() != null ? new ArrayList<>(service.getUrls()) : new ArrayList<>();

            List<KubeAIFeature> features = functionSpec.getFeatures() != null
                ? functionSpec.getFeatures()
                : Collections.emptyList();
            if (features.contains(KubeAIFeature.TextGeneration)) {
                urls.add(baseUrl + "/v1/chat/completions");
                urls.add(baseUrl + "/v1/completions");
            }
            if (features.contains(KubeAIFeature.TextEmbedding)) {
                urls.add(baseUrl + "/v1/embeddings");
            }

            service.setUrls(urls);
            status.setService(service);
        }

        //update state every time
        if (runnable != null && runnable instanceof K8sCRRunnable) {
            K8sCRRunnable k8sRunnable = (K8sCRRunnable) runnable;

            ModelStatus modelStatus = getModelStatus(k8sRunnable);
            if (modelStatus != null) {
                if (modelStatus.ready() > 0) {
                    status.setState(RunState.RUNNING.name());
                    status.setMessage("Model %s ready".formatted(functionSpec.getModelName()));
                } else {
                    status.setState(RunState.PENDING.name());
                    status.setMessage("Model %s not ready".formatted(functionSpec.getModelName()));
                }
            }
        }

        return status;
    }

    @Override
    public boolean isSupported(@NotNull Run run) {
        return KubeAITextRunSpec.KIND.equals(run.getKind());
    }
}
