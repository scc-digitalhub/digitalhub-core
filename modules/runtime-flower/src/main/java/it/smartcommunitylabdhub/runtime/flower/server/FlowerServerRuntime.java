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

package it.smartcommunitylabdhub.runtime.flower.server;

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
import it.smartcommunitylabdhub.commons.services.ConfigurationService;
import it.smartcommunitylabdhub.commons.services.FunctionManager;
import it.smartcommunitylabdhub.commons.services.SecretService;
import it.smartcommunitylabdhub.framework.k8s.base.K8sBaseRuntime;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sContainerBuilderRunnable;
import it.smartcommunitylabdhub.runtime.flower.server.specs.FlowerServerBuildRunSpec;
import it.smartcommunitylabdhub.runtime.flower.server.specs.FlowerServerBuildTaskSpec;
import it.smartcommunitylabdhub.runtime.flower.server.specs.FlowerServerDeployRunSpec;
import it.smartcommunitylabdhub.runtime.flower.server.specs.FlowerServerDeployTaskSpec;
import it.smartcommunitylabdhub.runtime.flower.server.specs.FlowerServerFunctionSpec;
import it.smartcommunitylabdhub.runtime.flower.server.specs.FlowerServerRunSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerRunStatus;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

@Slf4j
@RuntimeComponent(runtime = FlowerServerRuntime.RUNTIME)
public class FlowerServerRuntime
    extends K8sBaseRuntime<FlowerServerFunctionSpec, FlowerServerRunSpec, FlowerRunStatus, K8sRunnable> {

    public static final String RUNTIME = "flower-server";
    public static final String[] KINDS = { FlowerServerBuildRunSpec.KIND, FlowerServerDeployRunSpec.KIND };

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

    public FlowerServerRuntime() {}

    @Override
    public FlowerServerRunSpec build(@NotNull Executable function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!isSupported(run)) {
            throw new IllegalArgumentException("Run kind {} unsupported".formatted(String.valueOf(run.getKind())));
        }

        FlowerServerFunctionSpec funSpec = new FlowerServerFunctionSpec(function.getSpec());

        //build run spec as defined
        FlowerServerRunSpec runSpec =
            switch (run.getKind()) {
                case FlowerServerDeployRunSpec.KIND -> {
                    yield new FlowerServerDeployRunSpec(run.getSpec());
                }
                case FlowerServerBuildRunSpec.KIND -> {
                    yield new FlowerServerBuildRunSpec(run.getSpec());
                }
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build task spec as defined
        Map<String, Serializable> taskSpec =
            switch (task.getKind()) {
                case FlowerServerDeployTaskSpec.KIND -> {
                    yield new FlowerServerDeployTaskSpec(task.getSpec()).toMap();
                }
                case FlowerServerBuildTaskSpec.KIND -> {
                    yield new FlowerServerBuildTaskSpec(task.getSpec()).toMap();
                }
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build run merging task spec overrides
        Map<String, Serializable> map = new HashMap<>();
        map.putAll(runSpec.toMap());
        taskSpec.forEach(map::putIfAbsent);
        //ensure function is not modified
        map.putAll(funSpec.toMap());

        //update run spec
        runSpec.configure(map);

        return runSpec;
    }

    @Override
    public K8sRunnable run(@NotNull Run run) {
        //check run kind
        if (!isSupported(run)) {
            throw new IllegalArgumentException("Run kind {} unsupported".formatted(String.valueOf(run.getKind())));
        }

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

        //read base task spec to extract secrets
        K8sFunctionTaskBaseSpec taskSpec = new K8sFunctionTaskBaseSpec();
        taskSpec.configure(run.getSpec());
        Map<String, String> secrets = secretService.getSecretData(run.getProject(), taskSpec.getSecrets());

        K8sRunnable runnable =
            switch (runAccessor.getTask()) {
                case FlowerServerDeployTaskSpec.KIND -> new FlowerServerDeployRunner(
                    images.get("server"),
                    userId,
                    groupId,
                    caCertContent,
                    caKeyContent,
                    tlsConfContent,
                    tlsIntDomain,
                    tlsExtDomain,
                    k8sBuilderHelper,
                    functionService
                )
                    .produce(run, secrets);
                case FlowerServerBuildTaskSpec.KIND -> new FlowerServerBuildRunner(
                    images.get("server"),
                    "flower-superlink",
                    k8sBuilderHelper
                )
                    .produce(run, secrets);
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

    @Override
    public boolean isSupported(@NotNull Run run) {
        return Arrays.asList(KINDS).contains(run.getKind());
    }
}
