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
import it.smartcommunitylabdhub.runtime.flower.runners.FlowerBuildClientRunner;
import it.smartcommunitylabdhub.runtime.flower.runners.FlowerClientRunner;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerBuildClientTaskSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerClientFunctionSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerClientTaskSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerClientRunSpec;
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
@RuntimeComponent(runtime = FlowerClientRuntime.RUNTIME)
public class FlowerClientRuntime extends K8sBaseRuntime<FlowerClientFunctionSpec, FlowerClientRunSpec, FlowerRunStatus, K8sRunnable> {

    public static final String RUNTIME = "flower-client";

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

    public FlowerClientRuntime() {
        super(FlowerClientRunSpec.KIND);
    }

    @Override
    public FlowerClientRunSpec build(@NotNull Executable function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!FlowerClientRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), FlowerClientRunSpec.KIND)
            );
        }

        FlowerClientFunctionSpec funSpec = new FlowerClientFunctionSpec(function.getSpec());
        FlowerClientRunSpec runSpec = new FlowerClientRunSpec(run.getSpec());

        String kind = task.getKind();

        //build task spec as defined
        TaskBaseSpec taskSpec =
            switch (kind) {
                case FlowerClientTaskSpec.KIND -> {
                    yield new FlowerClientTaskSpec(task.getSpec());
                }
                case FlowerBuildClientTaskSpec.KIND -> {
                    yield new FlowerBuildClientTaskSpec(task.getSpec());
                }
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build run merging task spec overrides
        Map<String, Serializable> map = new HashMap<>();
        map.putAll(runSpec.toMap());
        taskSpec.toMap().forEach(map::putIfAbsent);

        FlowerClientRunSpec flowerSpec = new FlowerClientRunSpec(map);
        //ensure function is not modified
        flowerSpec.setFunctionSpec(funSpec);

        return flowerSpec;
    }

    @Override
    public K8sRunnable run(@NotNull Run run) {
        //check run kind
        if (!FlowerClientRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), FlowerClientRunSpec.KIND)
            );
        }

        FlowerClientRunSpec runFlowerSpec = new FlowerClientRunSpec(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        K8sRunnable runnable =
            switch (runAccessor.getTask()) {
                case FlowerClientTaskSpec.KIND -> new FlowerClientRunner(
                    images.get("client"),
                    userId,
                    groupId,
                    runFlowerSpec.getFunctionSpec(),
                    secretService.getSecretData(run.getProject(), runFlowerSpec.getTaskDeploySpec().getSecrets()),
                    k8sBuilderHelper
                )
                    .produce(run);
                case FlowerBuildClientTaskSpec.KIND -> new FlowerBuildClientRunner(
                    images.get("client"),
                    "flower-supernode",
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
            String kind = runAccessor.getTask();

            log.debug("update function {} spec to use built image: {}", functionId, image);

            FlowerClientFunctionSpec funSpec = new FlowerClientFunctionSpec(function.getSpec());
            if (!image.equals(funSpec.getImage())) {
                funSpec.setImage(image);
                function.setSpec(funSpec.toMap());
                functionService.updateFunction(functionId, function, true);
            }
        }

        return null;
    }
}
