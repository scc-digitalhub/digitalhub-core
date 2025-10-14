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

package it.smartcommunitylabdhub.runtime.kubeai.base;

import it.smartcommunitylabdhub.authorization.services.CredentialsService;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.services.ConfigurationService;
import it.smartcommunitylabdhub.commons.services.ModelManager;
import it.smartcommunitylabdhub.commons.services.SecretService;
import it.smartcommunitylabdhub.framework.k8s.base.K8sBaseRuntime;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sSecretHelper;
import it.smartcommunitylabdhub.framework.k8s.model.K8sServiceInfo;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runtime.kubeai.models.KubeAIAdapter;
import it.smartcommunitylabdhub.runtime.kubeai.models.OpenAIService;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
public abstract class KubeAIRuntime<F extends KubeAIServeFunctionSpec, R extends KubeAIServeRunSpec>
    extends K8sBaseRuntime<F, R, KubeAIServeRunStatus, K8sCRRunnable>
    implements InitializingBean {

    @Value("${runtime.kubeai.endpoint}")
    protected String kubeAiEndpoint;

    @Autowired
    protected ModelManager modelService;

    @Autowired
    protected SecretService secretService;

    @Autowired
    protected CredentialsService credentialsService;

    @Autowired
    protected ConfigurationService configurationService;

    @Autowired(required = false)
    protected K8sSecretHelper k8sSecretHelper;

    protected KubeAIRuntime() {}

    @Override
    public void afterPropertiesSet() throws Exception {
        // nothing to do
    }

    @Override
    public KubeAIServeRunStatus onRunning(@NotNull Run run, RunRunnable runnable) {
        KubeAIServeRunStatus status = KubeAIServeRunStatus.with(run.getStatus());

        KubeAIServeFunctionSpec functionSpec = KubeAIServeFunctionSpec.with(run.getSpec());
        if (functionSpec.getModelName() == null) {
            //error
            return null;
        }

        //build openapi descriptor only once
        if (status.getOpenai() == null) {
            OpenAIService openai = new OpenAIService();
            openai.setBaseUrl(kubeAiEndpoint + "/openai/v1");

            openai.setModel(functionSpec.getModelName());
            openai.setModelUrl(functionSpec.getUrl());

            if (runnable != null && runnable instanceof K8sCRRunnable) {
                //model name is set via resource name for kubeAi
                openai.setModel(((K8sCRRunnable) runnable).getName());
            }

            if (functionSpec.getAdapters() != null) {
                openai.setAdapters(
                    functionSpec
                        .getAdapters()
                        .stream()
                        .map(a -> KubeAIAdapter.builder().name(a.getName()).build())
                        .toList()
                );
            }

            status.setOpenai(openai);
        }

        if (status.getService() == null) {
            String baseUrl = kubeAiEndpoint + "/openai";
            K8sServiceInfo service = new K8sServiceInfo();
            service.setUrl(baseUrl);

            List<String> urls = new ArrayList<>();
            //model always available
            urls.add(baseUrl + "/v1/models");
            service.setUrls(urls);

            status.setService(service);
        }

        if (runnable != null && runnable instanceof K8sRunnable) {
            K8sRunnable k8sRunnable = (K8sRunnable) runnable;

            status.setState(k8sRunnable.getState());
            status.setMessage(k8sRunnable.getMessage());
        }

        if (runnable != null && runnable instanceof K8sCRRunnable) {
            //TODO fetch status to read replicas
            // K8sCRRunnable crRunnable = (K8sCRRunnable) runnable;
            // crRunnable.getStatus();

        }

        return status;
    }

    @SuppressWarnings("unchecked")
    protected ModelStatus getModelStatus(K8sCRRunnable k8sRunnable) {
        //update state every time
        if (k8sRunnable != null) {
            //check model replication status
            Optional<ModelStatus> status = Optional
                .ofNullable(k8sRunnable.getResults())
                .map(s -> s.get("Model"))
                .map(m -> ((Map<String, Serializable>) m).get("status"))
                .map(m -> ((Map<String, Serializable>) m).get("replicas"))
                .map(m -> ((Map<String, Serializable>) m).get("ready"))
                .map(ready -> {
                    if (ready instanceof Integer r) {
                        return new ModelStatus(r);
                    } else {
                        return null;
                    }
                });

            if (status.isPresent()) {
                return status.get();
            }
        }

        return null;
    }

    public record ModelStatus(int ready) {}
}
