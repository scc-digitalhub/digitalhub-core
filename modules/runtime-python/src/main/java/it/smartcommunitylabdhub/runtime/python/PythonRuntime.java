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

package it.smartcommunitylabdhub.runtime.python;

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
import it.smartcommunitylabdhub.runtime.python.runners.PythonBuildRunner;
import it.smartcommunitylabdhub.runtime.python.runners.PythonJobRunner;
import it.smartcommunitylabdhub.runtime.python.runners.PythonServeRunner;
import it.smartcommunitylabdhub.runtime.python.specs.PythonBuildTaskSpec;
import it.smartcommunitylabdhub.runtime.python.specs.PythonFunctionSpec;
import it.smartcommunitylabdhub.runtime.python.specs.PythonJobTaskSpec;
import it.smartcommunitylabdhub.runtime.python.specs.PythonRunSpec;
import it.smartcommunitylabdhub.runtime.python.specs.PythonRunStatus;
import it.smartcommunitylabdhub.runtime.python.specs.PythonServeTaskSpec;
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
@RuntimeComponent(runtime = PythonRuntime.RUNTIME)
public class PythonRuntime extends K8sBaseRuntime<PythonFunctionSpec, PythonRunSpec, PythonRunStatus, K8sRunnable> {

    public static final String RUNTIME = "python";

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

    @Value("${runtime.python.command}")
    private String command;

    @Value("${runtime.python.user-id}")
    private Integer userId;

    @Value("${runtime.python.group-id}")
    private Integer groupId;

    public PythonRuntime() {
        super(PythonRunSpec.KIND);
    }

    @Override
    public PythonRunSpec build(@NotNull Executable function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!PythonRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), PythonRunSpec.KIND)
            );
        }

        PythonFunctionSpec funSpec = new PythonFunctionSpec(function.getSpec());
        PythonRunSpec runSpec = new PythonRunSpec(run.getSpec());

        String kind = task.getKind();

        //build task spec as defined
        TaskBaseSpec taskSpec =
            switch (kind) {
                case PythonJobTaskSpec.KIND -> {
                    yield new PythonJobTaskSpec(task.getSpec());
                }
                case PythonServeTaskSpec.KIND -> {
                    yield new PythonServeTaskSpec(task.getSpec());
                }
                case PythonBuildTaskSpec.KIND -> {
                    yield new PythonBuildTaskSpec(task.getSpec());
                }
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build run merging task spec overrides
        Map<String, Serializable> map = new HashMap<>();
        map.putAll(runSpec.toMap());
        taskSpec.toMap().forEach(map::putIfAbsent);

        PythonRunSpec pythonSpec = new PythonRunSpec(map);
        //ensure function is not modified
        pythonSpec.setFunctionSpec(funSpec);

        return pythonSpec;
    }

    @Override
    public K8sRunnable run(@NotNull Run run) {
        //check run kind
        if (!PythonRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), PythonRunSpec.KIND)
            );
        }

        PythonRunSpec runPythonSpec = new PythonRunSpec(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        K8sRunnable runnable =
            switch (runAccessor.getTask()) {
                case PythonJobTaskSpec.KIND -> new PythonJobRunner(
                    images.get(runPythonSpec.getFunctionSpec().getPythonVersion().name()),
                    userId,
                    groupId,
                    command,
                    runPythonSpec.getFunctionSpec(),
                    secretService.getSecretData(run.getProject(), runPythonSpec.getTaskJobSpec().getSecrets()),
                    k8sBuilderHelper
                )
                    .produce(run);
                case PythonServeTaskSpec.KIND -> new PythonServeRunner(
                    images.get(runPythonSpec.getFunctionSpec().getPythonVersion().name()),
                    userId,
                    groupId,
                    command,
                    runPythonSpec.getFunctionSpec(),
                    secretService.getSecretData(run.getProject(), runPythonSpec.getTaskJobSpec().getSecrets()),
                    k8sBuilderHelper
                )
                    .produce(run);
                case PythonBuildTaskSpec.KIND -> new PythonBuildRunner(
                    images.get(runPythonSpec.getFunctionSpec().getPythonVersion().name()),
                    command,
                    runPythonSpec.getFunctionSpec(),
                    secretService.getSecretData(run.getProject(), runPythonSpec.getTaskBuildSpec().getSecrets()),
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
    public PythonRunStatus onComplete(Run run, RunRunnable runnable) {
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        //update image name after build
        if (runnable instanceof K8sContainerBuilderRunnable) {
            String image = ((K8sContainerBuilderRunnable) runnable).getImage();

            String functionId = runAccessor.getFunctionId();
            Function function = functionService.getFunction(functionId);

            log.debug("update function {} spec to use built image: {}", functionId, image);

            PythonFunctionSpec funSpec = new PythonFunctionSpec(function.getSpec());
            if (!image.equals(funSpec.getImage())) {
                funSpec.setImage(image);
                function.setSpec(funSpec.toMap());
                functionService.updateFunction(functionId, function, true);
            }
        }

        return null;
    }
}
