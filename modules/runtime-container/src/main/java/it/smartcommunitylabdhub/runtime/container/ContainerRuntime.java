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

package it.smartcommunitylabdhub.runtime.container;

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
import it.smartcommunitylabdhub.runtime.container.runners.ContainerBuildRunner;
import it.smartcommunitylabdhub.runtime.container.runners.ContainerDeployRunner;
import it.smartcommunitylabdhub.runtime.container.runners.ContainerJobRunner;
import it.smartcommunitylabdhub.runtime.container.runners.ContainerServeRunner;
import it.smartcommunitylabdhub.runtime.container.specs.ContainerBuildTaskSpec;
import it.smartcommunitylabdhub.runtime.container.specs.ContainerDeployTaskSpec;
import it.smartcommunitylabdhub.runtime.container.specs.ContainerFunctionSpec;
import it.smartcommunitylabdhub.runtime.container.specs.ContainerJobTaskSpec;
import it.smartcommunitylabdhub.runtime.container.specs.ContainerRunSpec;
import it.smartcommunitylabdhub.runtime.container.specs.ContainerRunStatus;
import it.smartcommunitylabdhub.runtime.container.specs.ContainerServeTaskSpec;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@RuntimeComponent(runtime = ContainerRuntime.RUNTIME)
public class ContainerRuntime
    extends K8sBaseRuntime<ContainerFunctionSpec, ContainerRunSpec, ContainerRunStatus, K8sRunnable> {

    public static final String RUNTIME = "container";

    //TODO make configurable
    public static final int MAX_METRICS = 300;

    @Autowired
    private SecretService secretService;

    @Autowired
    private FunctionManager functionService;

    @Autowired
    private CredentialsService credentialsService;

    @Autowired
    private ConfigurationService configurationService;

    public ContainerRuntime() {
        super(ContainerRunSpec.KIND);
    }

    @Override
    public ContainerRunSpec build(@NotNull Executable function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!ContainerRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), ContainerRunSpec.KIND)
            );
        }

        ContainerFunctionSpec funSpec = new ContainerFunctionSpec(function.getSpec());
        ContainerRunSpec runSpec = new ContainerRunSpec(run.getSpec());

        String kind = task.getKind();

        //build task spec as defined
        TaskBaseSpec taskSpec =
            switch (kind) {
                case ContainerDeployTaskSpec.KIND -> {
                    yield new ContainerDeployTaskSpec(task.getSpec());
                }
                case ContainerJobTaskSpec.KIND -> {
                    yield new ContainerJobTaskSpec(task.getSpec());
                }
                case ContainerServeTaskSpec.KIND -> {
                    yield new ContainerServeTaskSpec(task.getSpec());
                }
                case ContainerBuildTaskSpec.KIND -> {
                    yield new ContainerBuildTaskSpec(task.getSpec());
                }
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build run merging task spec overrides
        Map<String, Serializable> map = new HashMap<>();
        map.putAll(runSpec.toMap());
        taskSpec.toMap().forEach(map::putIfAbsent);

        ContainerRunSpec containerSpec = new ContainerRunSpec(map);
        //ensure function is not modified
        containerSpec.setFunctionSpec(funSpec);

        return containerSpec;
    }

    @Override
    public K8sRunnable run(@NotNull Run run) {
        //check run kind
        if (!ContainerRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), ContainerRunSpec.KIND)
            );
        }

        ContainerRunSpec runContainerSpec = new ContainerRunSpec(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        K8sRunnable runnable =
            switch (runAccessor.getTask()) {
                case ContainerDeployTaskSpec.KIND -> new ContainerDeployRunner(
                    runContainerSpec.getFunctionSpec(),
                    secretService.getSecretData(run.getProject(), runContainerSpec.getTaskDeploySpec().getSecrets()),
                    k8sBuilderHelper
                )
                    .produce(run);
                case ContainerJobTaskSpec.KIND -> new ContainerJobRunner(
                    runContainerSpec.getFunctionSpec(),
                    secretService.getSecretData(run.getProject(), runContainerSpec.getTaskJobSpec().getSecrets()),
                    k8sBuilderHelper
                )
                    .produce(run);
                case ContainerServeTaskSpec.KIND -> new ContainerServeRunner(
                    runContainerSpec.getFunctionSpec(),
                    secretService.getSecretData(run.getProject(), runContainerSpec.getTaskServeSpec().getSecrets()),
                    k8sBuilderHelper
                )
                    .produce(run);
                case ContainerBuildTaskSpec.KIND -> new ContainerBuildRunner(
                    runContainerSpec.getFunctionSpec(),
                    secretService.getSecretData(run.getProject(), runContainerSpec.getTaskBuildSpec().getSecrets()),
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
    public ContainerRunStatus onComplete(Run run, RunRunnable runnable) {
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        //update image name after build
        if (runnable instanceof K8sContainerBuilderRunnable) {
            String image = ((K8sContainerBuilderRunnable) runnable).getImage();

            String functionId = runAccessor.getFunctionId();
            Function function = functionService.getFunction(functionId);

            log.debug("update function {} spec to use built image: {}", functionId, image);

            ContainerFunctionSpec funSpec = new ContainerFunctionSpec(function.getSpec());
            if (!image.equals(funSpec.getImage())) {
                funSpec.setImage(image);
                function.setSpec(funSpec.toMap());
                functionService.updateFunction(functionId, function, true);
            }
        }
        return null;
    }
}
