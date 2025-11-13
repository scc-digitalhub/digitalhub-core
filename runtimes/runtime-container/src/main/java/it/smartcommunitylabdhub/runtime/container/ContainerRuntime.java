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
import it.smartcommunitylabdhub.commons.services.ConfigurationService;
import it.smartcommunitylabdhub.commons.services.FunctionManager;
import it.smartcommunitylabdhub.commons.services.SecretService;
import it.smartcommunitylabdhub.framework.k8s.base.K8sBaseRuntime;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sContainerBuilderRunnable;
import it.smartcommunitylabdhub.runtime.container.build.ContainerBuildRunSpec;
import it.smartcommunitylabdhub.runtime.container.build.ContainerBuildRunner;
import it.smartcommunitylabdhub.runtime.container.build.ContainerBuildTaskSpec;
import it.smartcommunitylabdhub.runtime.container.deploy.ContainerDeployRunSpec;
import it.smartcommunitylabdhub.runtime.container.deploy.ContainerDeployRunner;
import it.smartcommunitylabdhub.runtime.container.deploy.ContainerDeployTaskSpec;
import it.smartcommunitylabdhub.runtime.container.job.ContainerJobRunSpec;
import it.smartcommunitylabdhub.runtime.container.job.ContainerJobRunner;
import it.smartcommunitylabdhub.runtime.container.job.ContainerJobTaskSpec;
import it.smartcommunitylabdhub.runtime.container.serve.ContainerServeRunSpec;
import it.smartcommunitylabdhub.runtime.container.serve.ContainerServeRunner;
import it.smartcommunitylabdhub.runtime.container.serve.ContainerServeTaskSpec;
import it.smartcommunitylabdhub.runtime.container.specs.ContainerFunctionSpec;
import it.smartcommunitylabdhub.runtime.container.specs.ContainerRunSpec;
import it.smartcommunitylabdhub.runtime.container.specs.ContainerRunStatus;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Arrays;
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
    public static final String[] KINDS = {
        ContainerBuildRunSpec.KIND,
        ContainerJobRunSpec.KIND,
        ContainerDeployRunSpec.KIND,
        ContainerServeRunSpec.KIND,
    };

    @Autowired
    private SecretService secretService;

    @Autowired
    private FunctionManager functionService;

    @Autowired
    private CredentialsService credentialsService;

    @Autowired
    private ConfigurationService configurationService;

    @Override
    public boolean isSupported(@NotNull Run run) {
        return Arrays.asList(KINDS).contains(run.getKind());
    }

    @Override
    public ContainerRunSpec build(@NotNull Executable function, @NotNull Task task, @NotNull Run run) {
        ContainerFunctionSpec funSpec = new ContainerFunctionSpec(function.getSpec());

        String kind = run.getKind();
        ContainerRunSpec runSpec =
            switch (kind) {
                case ContainerDeployRunSpec.KIND -> {
                    yield new ContainerDeployRunSpec(run.getSpec());
                }
                case ContainerJobRunSpec.KIND -> {
                    yield new ContainerJobRunSpec(run.getSpec());
                }
                case ContainerServeRunSpec.KIND -> {
                    yield new ContainerServeRunSpec(run.getSpec());
                }
                case ContainerBuildRunSpec.KIND -> {
                    yield new ContainerBuildRunSpec(run.getSpec());
                }
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build task spec as defined
        Map<String, Serializable> taskSpec =
            switch (task.getKind()) {
                case ContainerDeployTaskSpec.KIND -> {
                    yield new ContainerDeployTaskSpec(task.getSpec()).toMap();
                }
                case ContainerJobTaskSpec.KIND -> {
                    yield new ContainerJobTaskSpec(task.getSpec()).toMap();
                }
                case ContainerServeTaskSpec.KIND -> {
                    yield new ContainerServeTaskSpec(task.getSpec()).toMap();
                }
                case ContainerBuildTaskSpec.KIND -> {
                    yield new ContainerBuildTaskSpec(task.getSpec()).toMap();
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

        runSpec.configure(map);

        return runSpec;
    }

    @Override
    public K8sRunnable run(@NotNull Run run) {
        //check run kind
        if (!isSupported(run)) {
            throw new IllegalArgumentException("Run kind {} unsupported".formatted(String.valueOf(run.getKind())));
        }

        if (k8sBuilderHelper == null) {
            throw new IllegalArgumentException("No k8s available");
        }

        //read base task spec to extract secrets
        K8sFunctionTaskBaseSpec taskSpec = new K8sFunctionTaskBaseSpec();
        taskSpec.configure(run.getSpec());
        Map<String, String> secrets = secretService.getSecretData(run.getProject(), taskSpec.getSecrets());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        K8sRunnable runnable =
            switch (runAccessor.getTask()) {
                case ContainerDeployTaskSpec.KIND -> new ContainerDeployRunner(k8sBuilderHelper).produce(run, secrets);
                case ContainerJobTaskSpec.KIND -> new ContainerJobRunner(k8sBuilderHelper).produce(run, secrets);
                case ContainerServeTaskSpec.KIND -> new ContainerServeRunner(k8sBuilderHelper, functionService)
                    .produce(run, secrets);
                case ContainerBuildTaskSpec.KIND -> new ContainerBuildRunner(k8sBuilderHelper).produce(run, secrets);
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
