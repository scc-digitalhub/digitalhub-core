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

package it.smartcommunitylabdhub.runtime.flower.app;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.CredentialsService;
import it.smartcommunitylabdhub.authorization.utils.UserAuthenticationHelper;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.commons.infrastructure.Configuration;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.models.base.Executable;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.services.ConfigurationService;
import it.smartcommunitylabdhub.commons.services.SecretService;
import it.smartcommunitylabdhub.framework.k8s.base.K8sBaseRuntime;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runtime.flower.app.specs.FlowerAppFunctionSpec;
import it.smartcommunitylabdhub.runtime.flower.app.specs.FlowerAppRunSpec;
import it.smartcommunitylabdhub.runtime.flower.app.specs.FlowerAppTrainTaskSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerRunStatus;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
@RuntimeComponent(runtime = FlowerAppRuntime.RUNTIME)
public class FlowerAppRuntime
    extends K8sBaseRuntime<FlowerAppFunctionSpec, FlowerAppRunSpec, FlowerRunStatus, K8sRunnable> {

    public static final String RUNTIME = "flower-app";

    @Autowired
    private SecretService secretService;

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

    public FlowerAppRuntime() {}

    @Override
    public FlowerAppRunSpec build(@NotNull Executable function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!FlowerAppRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), FlowerAppRunSpec.KIND)
            );
        }

        FlowerAppFunctionSpec funSpec = new FlowerAppFunctionSpec(function.getSpec());
        FlowerAppRunSpec runSpec = new FlowerAppRunSpec(run.getSpec());

        String kind = task.getKind();

        //build task spec as defined
        Map<String, Serializable> taskSpec =
            switch (kind) {
                case FlowerAppTrainTaskSpec.KIND -> {
                    yield new FlowerAppTrainTaskSpec(task.getSpec()).toMap();
                }
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build run merging task spec overrides
        Map<String, Serializable> map = new HashMap<>();
        map.putAll(runSpec.toMap());
        taskSpec.forEach(map::putIfAbsent);

        FlowerAppRunSpec appSpec = new FlowerAppRunSpec(map);
        //ensure function is not modified
        appSpec.setFunctionSpec(funSpec);

        return appSpec;
    }

    @Override
    public K8sRunnable run(@NotNull Run run) {
        //check run kind
        if (!FlowerAppRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), FlowerAppRunSpec.KIND)
            );
        }

        FlowerAppRunSpec runFlowerSpec = new FlowerAppRunSpec(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        K8sRunnable runnable =
            switch (runAccessor.getTask()) {
                case FlowerAppTrainTaskSpec.KIND -> new FlowerAppTrainRunner(
                    images.get("runner"),
                    userId,
                    groupId,
                    k8sBuilderHelper
                )
                    .produce(
                        run,
                        secretService.getSecretData(run.getProject(), runFlowerSpec.getTaskTrainSpec().getSecrets())
                    );
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
        return FlowerAppRunSpec.KIND.equals(run.getKind());
    }
}
