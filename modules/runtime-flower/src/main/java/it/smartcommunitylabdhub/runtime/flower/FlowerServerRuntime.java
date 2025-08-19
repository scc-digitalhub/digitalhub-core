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

package it.smartcommunitylabdhub.runtime.flower;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.CredentialsService;
import it.smartcommunitylabdhub.authorization.utils.UserAuthenticationHelper;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.commons.infrastructure.Configuration;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.models.base.Executable;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.models.task.TaskBaseSpec;
import it.smartcommunitylabdhub.commons.services.ConfigurationService;
import it.smartcommunitylabdhub.commons.services.FunctionManager;
import it.smartcommunitylabdhub.commons.services.SecretService;
import it.smartcommunitylabdhub.framework.k8s.base.K8sBaseRuntime;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sContainerBuilderRunnable;
import it.smartcommunitylabdhub.runtime.flower.runners.FlowerBuildServerRunner;
import it.smartcommunitylabdhub.runtime.flower.runners.FlowerServerRunner;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerBuildServerTaskSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerServerFunctionSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerServerRunSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerRunStatus;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerServerTaskSpec;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.endpoints.internal.Value.Str;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

@Slf4j
@RuntimeComponent(runtime = FlowerServerRuntime.RUNTIME)
public class FlowerServerRuntime extends K8sBaseRuntime<FlowerServerFunctionSpec, FlowerServerRunSpec, FlowerRunStatus, K8sRunnable> {

    public static final String RUNTIME = "flower-server";

    @Autowired
    private SecretService secretService;

    @Autowired
    private FunctionManager functionService;

    @Autowired
    private CredentialsService credentialsService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    @Qualifier("flowerImages")
    private Map<String, String> images;


    @Value("${runtime.flower.user-id}")
    private Integer userId;

    @Value("${runtime.flower.group-id}")
    private Integer groupId;

    @Value("${runtime.flower.tls-ca-cert:}")
    private Resource caCert;  
    @Value("${runtime.flower.tls-ca-key:}")
    private Resource caKey;  
    @Value("${runtime.flower.tls-conf:}")
    private Resource tlsConf;
    @Value("${runtime.flower.tls-int-domain:}") 
    private String tlsIntDomain;
    @Value("${runtime.flower.tls-ext-domain:}")
    private String tlsExtDomain;

    public FlowerServerRuntime() {
        super(FlowerServerRunSpec.KIND);
    }

    @Override
    public FlowerServerRunSpec build(@NotNull Executable function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!FlowerServerRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), FlowerServerRunSpec.KIND)
            );
        }

        FlowerServerFunctionSpec funSpec = new FlowerServerFunctionSpec(function.getSpec());
        FlowerServerRunSpec runSpec = new FlowerServerRunSpec(run.getSpec());

        String kind = task.getKind();

        //build task spec as defined
        TaskBaseSpec taskSpec =
            switch (kind) {
                case FlowerServerTaskSpec.KIND -> {
                    yield new FlowerServerTaskSpec(task.getSpec());
                }
                case FlowerBuildServerTaskSpec.KIND -> {
                    yield new FlowerBuildServerTaskSpec(task.getSpec());
                }
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build run merging task spec overrides
        Map<String, Serializable> map = new HashMap<>();
        map.putAll(runSpec.toMap());
        taskSpec.toMap().forEach(map::putIfAbsent);

        FlowerServerRunSpec pythonSpec = new FlowerServerRunSpec(map);
        //ensure function is not modified
        pythonSpec.setFunctionSpec(funSpec);

        return pythonSpec;
    }

    @Override
    public K8sRunnable run(@NotNull Run run) {
        //check run kind
        if (!FlowerServerRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), FlowerServerRunSpec.KIND)
            );
        }

        FlowerServerRunSpec runFlowerSpec = new FlowerServerRunSpec(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        String caCertContent = null;
        String caKeyContent = null;
        String tlsConfContent = null;
        try {
            caCertContent = caCert == null ? null : caCert.getContentAsString(StandardCharsets.UTF_8);
            caKeyContent = caKey == null ? null : caKey.getContentAsString(StandardCharsets.UTF_8);
            tlsConfContent = tlsConf == null ? null : tlsConf.getContentAsString(StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read certificate files", e);
        }

        K8sRunnable runnable =
            switch (runAccessor.getTask()) {
                case FlowerServerTaskSpec.KIND -> new FlowerServerRunner(
                    images.get("server"),
                    userId,
                    groupId,
                    caCertContent,
                    caKeyContent,
                    tlsConfContent,
                    tlsIntDomain,
                    tlsExtDomain,
                    runFlowerSpec.getFunctionSpec(),
                    secretService.getSecretData(run.getProject(), runFlowerSpec.getTaskDeploySpec().getSecrets()),
                    k8sBuilderHelper,
                    functionService
                )
                    .produce(run);
                case FlowerBuildServerTaskSpec.KIND -> new FlowerBuildServerRunner(
                    images.get("server"),
                    "flower-superlink",
                    runFlowerSpec.getFunctionSpec(),
                    secretService.getSecretData(run.getProject(), runFlowerSpec.getTaskBuildSpec().getSecrets()),
                    k8sBuilderHelper
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
    public FlowerRunStatus onComplete(Run run, RunRunnable runnable) {
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        //update image name after build
        if (runnable instanceof K8sContainerBuilderRunnable) {
            String image = ((K8sContainerBuilderRunnable) runnable).getImage();

            String functionId = runAccessor.getFunctionId();
            Function function = functionService.getFunction(functionId);

            log.debug("update function {} spec to use built image: {}", functionId, image);

            FlowerServerFunctionSpec funSpec = new FlowerServerFunctionSpec(function.getSpec());
            if (!image.equals(funSpec.getImage())) {
                funSpec.setImage(image);
                function.setSpec(funSpec.toMap());
                functionService.updateFunction(functionId, function, true);
            }
        }

        return null;
    }
}
