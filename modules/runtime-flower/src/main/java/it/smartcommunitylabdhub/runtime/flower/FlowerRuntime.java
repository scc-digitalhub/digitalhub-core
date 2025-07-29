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
import it.smartcommunitylabdhub.runtime.flower.runners.FlowerBuildServerRunner;
import it.smartcommunitylabdhub.runtime.flower.runners.FlowerClientRunner;
import it.smartcommunitylabdhub.runtime.flower.runners.FlowerServerRunner;
import it.smartcommunitylabdhub.runtime.flower.runners.FlowerTrainRunner;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerBuildClientTaskSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerBuildServerTaskSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerClientTaskSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerFunctionSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerRunSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerRunStatus;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerServerTaskSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerTrainTaskSpec;
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
@RuntimeComponent(runtime = FlowerRuntime.RUNTIME)
public class FlowerRuntime extends K8sBaseRuntime<FlowerFunctionSpec, FlowerRunSpec, FlowerRunStatus, K8sRunnable> {

    public static final String RUNTIME = "flower";

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

    public FlowerRuntime() {
        super(FlowerRunSpec.KIND);
    }

    @Override
    public FlowerRunSpec build(@NotNull Executable function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!FlowerRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), FlowerRunSpec.KIND)
            );
        }

        FlowerFunctionSpec funSpec = new FlowerFunctionSpec(function.getSpec());
        FlowerRunSpec runSpec = new FlowerRunSpec(run.getSpec());

        String kind = task.getKind();

        //build task spec as defined
        TaskBaseSpec taskSpec =
            switch (kind) {
                case FlowerTrainTaskSpec.KIND -> {
                    yield new FlowerTrainTaskSpec(task.getSpec());
                }
                case FlowerClientTaskSpec.KIND -> {
                    yield new FlowerClientTaskSpec(task.getSpec());
                }
                case FlowerServerTaskSpec.KIND -> {
                    yield new FlowerServerTaskSpec(task.getSpec());
                }
                case FlowerBuildClientTaskSpec.KIND -> {
                    yield new FlowerBuildClientTaskSpec(task.getSpec());
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

        FlowerRunSpec pythonSpec = new FlowerRunSpec(map);
        //ensure function is not modified
        pythonSpec.setFunctionSpec(funSpec);

        return pythonSpec;
    }

    @Override
    public K8sRunnable run(@NotNull Run run) {
        //check run kind
        if (!FlowerRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), FlowerRunSpec.KIND)
            );
        }

        FlowerRunSpec runFlowerSpec = new FlowerRunSpec(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        K8sRunnable runnable =
            switch (runAccessor.getTask()) {
                case FlowerTrainTaskSpec.KIND -> new FlowerTrainRunner(
                    images.get("runner"),
                    userId,
                    groupId,
                    runFlowerSpec.getFunctionSpec(),
                    secretService.getSecretData(run.getProject(), runFlowerSpec.getTaskTrainSpec().getSecrets()),
                    k8sBuilderHelper
                )
                    .produce(run);
                case FlowerServerTaskSpec.KIND -> new FlowerServerRunner(
                    userId,
                    groupId,
                    runFlowerSpec.getFunctionSpec(),
                    secretService.getSecretData(run.getProject(), runFlowerSpec.getTaskServerSpec().getSecrets()),
                    k8sBuilderHelper,
                    functionService
                )
                    .produce(run);
                case FlowerClientTaskSpec.KIND -> new FlowerClientRunner(
                    userId,
                    groupId,
                    runFlowerSpec.getFunctionSpec(),
                    secretService.getSecretData(run.getProject(), runFlowerSpec.getTaskClientSpec().getSecrets()),
                    k8sBuilderHelper
                )
                    .produce(run);
                case FlowerBuildServerTaskSpec.KIND -> new FlowerBuildServerRunner(
                    images.get("server"),
                    "flower-superlink",
                    runFlowerSpec.getFunctionSpec(),
                    secretService.getSecretData(run.getProject(), runFlowerSpec.getTaskBuildServerSpec().getSecrets()),
                    k8sBuilderHelper
                )
                    .produce(run);
                case FlowerBuildClientTaskSpec.KIND -> new FlowerBuildClientRunner(
                    images.get("client"),
                    "flower-supernode",
                    runFlowerSpec.getFunctionSpec(),
                    secretService.getSecretData(run.getProject(), runFlowerSpec.getTaskBuildClientSpec().getSecrets()),
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

            FlowerFunctionSpec funSpec = new FlowerFunctionSpec(function.getSpec());
            if (kind.equals(FlowerBuildClientTaskSpec.KIND) && !image.equals(funSpec.getClientImage())) {
                funSpec.setClientImage(image);
                function.setSpec(funSpec.toMap());
                functionService.updateFunction(functionId, function, true);
            } else if (kind.equals(FlowerBuildServerTaskSpec.KIND) && !image.equals(funSpec.getServerImage())) {
                funSpec.setServerImage(image);
                function.setSpec(funSpec.toMap());
                functionService.updateFunction(functionId, function, true);
            }
        }

        return null;
    }
}
