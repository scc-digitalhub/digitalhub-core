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

package it.smartcommunitylabdhub.runtime.sklearn;

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
import it.smartcommunitylabdhub.commons.models.task.TaskBaseSpec;
import it.smartcommunitylabdhub.commons.services.ConfigurationService;
import it.smartcommunitylabdhub.commons.services.FunctionManager;
import it.smartcommunitylabdhub.commons.services.ModelManager;
import it.smartcommunitylabdhub.commons.services.SecretService;
import it.smartcommunitylabdhub.framework.k8s.base.K8sBaseRuntime;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runtime.modelserve.specs.ModelServeRunStatus;
import it.smartcommunitylabdhub.runtime.sklearn.specs.SklearnServeFunctionSpec;
import it.smartcommunitylabdhub.runtime.sklearn.specs.SklearnServeRunSpec;
import it.smartcommunitylabdhub.runtime.sklearn.specs.SklearnServeTaskSpec;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;

@Slf4j
@RuntimeComponent(runtime = SklearnServeRuntime.RUNTIME)
public class SklearnServeRuntime
    extends K8sBaseRuntime<SklearnServeFunctionSpec, SklearnServeRunSpec, ModelServeRunStatus, K8sRunnable>
    implements InitializingBean {

    public static final String RUNTIME = "sklearnserve";
    public static final String IMAGE = "seldonio/mlserver";

    @Autowired
    private SecretService secretService;

    @Autowired
    private ModelManager modelService;

    @Autowired
    private FunctionManager functionService;

    @Autowired
    private CredentialsService credentialsService;

    @Autowired
    private ConfigurationService configurationService;

    @Value("${runtime.sklearnserve.image}")
    private String image;

    @Value("${runtime.sklearnserve.user-id}")
    private Integer userId;

    @Value("${runtime.sklearnserve.group-id}")
    private Integer groupId;

    public SklearnServeRuntime() {
        super(SklearnServeRunSpec.KIND);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.hasText(image, "image can not be null or empty");
        Assert.isTrue(image.startsWith(IMAGE), "image must be a version of " + IMAGE);
    }

    @Override
    public SklearnServeRunSpec build(@NotNull Executable function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!SklearnServeRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(
                        String.valueOf(run.getKind()),
                        SklearnServeRunSpec.KIND
                    )
            );
        }

        SklearnServeFunctionSpec funSpec = SklearnServeFunctionSpec.with(function.getSpec());
        SklearnServeRunSpec runSpec = SklearnServeRunSpec.with(run.getSpec());

        String kind = task.getKind();

        //build task spec as defined
        TaskBaseSpec taskSpec =
            switch (kind) {
                case SklearnServeTaskSpec.KIND -> {
                    yield SklearnServeTaskSpec.with(task.getSpec());
                }
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build run merging task spec overrides
        Map<String, Serializable> map = new HashMap<>();
        map.putAll(runSpec.toMap());
        taskSpec.toMap().forEach(map::putIfAbsent);

        SklearnServeRunSpec serveSpec = SklearnServeRunSpec.with(map);
        //ensure function is not modified
        serveSpec.setFunctionSpec(funSpec);

        return serveSpec;
    }

    @Override
    public K8sRunnable run(@NotNull Run run) {
        //check run kind
        if (!SklearnServeRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(
                        String.valueOf(run.getKind()),
                        SklearnServeRunSpec.KIND
                    )
            );
        }

        SklearnServeRunSpec runSpec = SklearnServeRunSpec.with(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        K8sRunnable runnable =
            switch (runAccessor.getTask()) {
                case SklearnServeTaskSpec.KIND -> new SklearnServeRunner(
                    image,
                    userId,
                    groupId,
                    runSpec.getFunctionSpec(),
                    secretService.getSecretData(run.getProject(), runSpec.getTaskServeSpec().getSecrets()),
                    k8sBuilderHelper,
                    modelService,
                    functionService
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
}
