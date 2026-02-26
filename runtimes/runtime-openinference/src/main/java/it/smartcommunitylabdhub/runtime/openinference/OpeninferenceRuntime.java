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

package it.smartcommunitylabdhub.runtime.openinference;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.CredentialsService;
import it.smartcommunitylabdhub.authorization.utils.UserAuthenticationHelper;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.commons.infrastructure.Configuration;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.services.ConfigurationService;
import it.smartcommunitylabdhub.commons.services.SecretService;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionBaseRuntime;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sContainerBuilderRunnable;
import it.smartcommunitylabdhub.functions.FunctionManager;
import it.smartcommunitylabdhub.runtime.openinference.runners.OpeninferenceBuildRunner;
import it.smartcommunitylabdhub.runtime.openinference.runners.OpeninferenceServeRunner;
import it.smartcommunitylabdhub.runtime.openinference.specs.OpeninferenceBuildRunSpec;
import it.smartcommunitylabdhub.runtime.openinference.specs.OpeninferenceBuildTaskSpec;
import it.smartcommunitylabdhub.runtime.openinference.specs.OpeninferenceFunctionSpec;
import it.smartcommunitylabdhub.runtime.openinference.specs.OpeninferenceRunSpec;
import it.smartcommunitylabdhub.runtime.openinference.specs.OpeninferenceRunStatus;
import it.smartcommunitylabdhub.runtime.openinference.specs.OpeninferenceServeRunSpec;
import it.smartcommunitylabdhub.runtime.openinference.specs.OpeninferenceServeTaskSpec;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
@RuntimeComponent(runtime = OpeninferenceRuntime.RUNTIME)
public class OpeninferenceRuntime
    extends K8sFunctionBaseRuntime<OpeninferenceFunctionSpec, OpeninferenceRunSpec, OpeninferenceRunStatus, K8sRunnable> {

    public static final String RUNTIME = "openinference";
    public static final String[] KINDS = { OpeninferenceServeRunSpec.KIND, OpeninferenceBuildRunSpec.KIND };
    @Autowired
    private SecretService secretService;

    @Autowired
    private FunctionManager functionService;

    @Autowired
    private CredentialsService credentialsService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    @Qualifier("pythonImages")
    private Map<String, String> images;

    @Autowired
    @Qualifier("pythonServerlessImages")
    private Map<String, String> serverlessImages;

    @Autowired
    @Qualifier("pythonBaseImages")
    private Map<String, String> baseImages;

    @Value("${runtime.python.command}")
    private String command;

    @Value("${runtime.python.user-id}")
    private Integer userId;

    @Value("${runtime.python.group-id}")
    private Integer groupId;

    @Value("${runtime.openinference.volume-size:1Gi}")
    protected String volumeSizeSpec;

    @Value("${runtime.openinference.dependencies}")
    private List<String> dependencies;


    public OpeninferenceRuntime() {}

    @Override
    public OpeninferenceRunSpec build(@NotNull Function function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!isSupported(run)) {
            throw new IllegalArgumentException("Run kind {} unsupported".formatted(String.valueOf(run.getKind())));
        }

        OpeninferenceFunctionSpec funSpec = new OpeninferenceFunctionSpec(function.getSpec());
        OpeninferenceRunSpec runSpec =
            switch (run.getKind()) {
                case OpeninferenceServeRunSpec.KIND -> new OpeninferenceServeRunSpec(run.getSpec());
                case OpeninferenceBuildRunSpec.KIND -> new OpeninferenceBuildRunSpec(run.getSpec());
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build task spec as defined
        Map<String, Serializable> taskSpec =
            switch (task.getKind()) {
                case OpeninferenceServeTaskSpec.KIND -> {
                    yield new OpeninferenceServeTaskSpec(task.getSpec()).toMap();
                }
                case OpeninferenceBuildTaskSpec.KIND -> {
                    yield new OpeninferenceBuildTaskSpec(task.getSpec()).toMap();
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

        //reconfigure run spec
        runSpec.configure(map);

        return runSpec;
    }

    @Override
    public K8sRunnable run(@NotNull Run run) {
        //check run kind
        if (!isSupported(run)) {
            throw new IllegalArgumentException("Run kind {} unsupported".formatted(String.valueOf(run.getKind())));
        }

        //read base task spec to extract secrets
        K8sFunctionTaskBaseSpec taskSpec = new K8sFunctionTaskBaseSpec();
        taskSpec.configure(run.getSpec());
        Map<String, String> secrets = secretService.getSecretData(run.getProject(), taskSpec.getSecrets());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        K8sRunnable runnable =
            switch (runAccessor.getTask()) {
                case OpeninferenceServeTaskSpec.KIND -> new OpeninferenceServeRunner(
                    images,
                    serverlessImages,
                    baseImages,
                    volumeSizeSpec, 
                    userId,
                    groupId,
                    command,
                    k8sBuilderHelper,
                    functionService,
                    dependencies
                )
                    .produce(run, secrets);
                case OpeninferenceBuildTaskSpec.KIND -> new OpeninferenceBuildRunner(images, serverlessImages, baseImages, command, k8sBuilderHelper, dependencies)
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
    public OpeninferenceRunStatus onComplete(Run run, RunRunnable runnable) {
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        //update image name after build
        if (runnable instanceof K8sContainerBuilderRunnable) {
            String image = ((K8sContainerBuilderRunnable) runnable).getImage();

            String functionId = runAccessor.getFunctionId();
            Function function = functionService.getFunction(functionId);

            log.debug("update function {} spec to use built image: {}", functionId, image);

            OpeninferenceFunctionSpec funSpec = new OpeninferenceFunctionSpec(function.getSpec());
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
