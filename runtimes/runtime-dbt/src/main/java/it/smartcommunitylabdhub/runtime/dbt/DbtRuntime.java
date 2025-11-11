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

package it.smartcommunitylabdhub.runtime.dbt;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.CredentialsService;
import it.smartcommunitylabdhub.authorization.utils.UserAuthenticationHelper;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Configuration;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.models.base.Executable;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.models.task.TaskBaseSpec;
import it.smartcommunitylabdhub.commons.services.ConfigurationService;
import it.smartcommunitylabdhub.commons.services.SecretService;
import it.smartcommunitylabdhub.framework.k8s.base.K8sBaseRuntime;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.runtime.dbt.runners.DbtTransformRunner;
import it.smartcommunitylabdhub.runtime.dbt.specs.DbtFunctionSpec;
import it.smartcommunitylabdhub.runtime.dbt.specs.DbtRunSpec;
import it.smartcommunitylabdhub.runtime.dbt.specs.DbtRunStatus;
import it.smartcommunitylabdhub.runtime.dbt.specs.DbtTransformSpec;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@RuntimeComponent(runtime = DbtRuntime.RUNTIME)
@Slf4j
public class DbtRuntime extends K8sBaseRuntime<DbtFunctionSpec, DbtRunSpec, DbtRunStatus, K8sJobRunnable> {

    public static final String RUNTIME = "dbt";

    @Autowired
    SecretService secretService;

    @Autowired
    private CredentialsService credentialsService;

    @Autowired
    private ConfigurationService configurationService;

    @Value("${runtime.dbt.image}")
    private String image;

    public DbtRuntime() {}

    @Override
    public DbtRunSpec build(@NotNull Executable function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!DbtRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), DbtRunSpec.KIND)
            );
        }

        log.debug("build run spec for run {}", run.getId());

        DbtFunctionSpec functionSpec = new DbtFunctionSpec(function.getSpec());
        DbtRunSpec runSpec = new DbtRunSpec(run.getSpec());

        String kind = task.getKind();

        //build task spec as defined
        TaskBaseSpec taskSpec =
            switch (kind) {
                case DbtTransformSpec.KIND -> {
                    yield new DbtTransformSpec(task.getSpec());
                }
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build run merging task spec overrides
        Map<String, Serializable> map = new HashMap<>();
        map.putAll(runSpec.toMap());
        taskSpec.toMap().forEach(map::putIfAbsent);

        DbtRunSpec dbtRunSpec = new DbtRunSpec(map);
        //ensure function is not modified
        dbtRunSpec.setFunctionSpec(functionSpec);

        return dbtRunSpec;
    }

    @Override
    public K8sJobRunnable run(Run run) {
        //check run kind
        if (!DbtRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), DbtRunSpec.KIND)
            );
        }

        log.debug("build runnable for run {}", run.getId());

        // Crete spec for run
        DbtRunSpec runSpec = new DbtRunSpec(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        K8sJobRunnable runnable =
            switch (runAccessor.getTask()) {
                case DbtTransformSpec.KIND -> {
                    DbtTransformSpec taskSpec = runSpec.getTaskSpec();
                    if (taskSpec == null) {
                        throw new CoreRuntimeException("null or empty task definition");
                    }

                    yield new DbtTransformRunner(image, k8sBuilderHelper)
                        .produce(run, secretService.getSecretData(run.getProject(), taskSpec.getSecrets()));
                }
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
        return DbtRunSpec.KIND.equals(run.getKind());
    }
}
