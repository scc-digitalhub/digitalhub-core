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

package it.smartcommunitylabdhub.runtime.mlflow;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.CredentialsService;
import it.smartcommunitylabdhub.authorization.utils.UserAuthenticationHelper;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.commons.infrastructure.Configuration;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.models.base.Executable;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.models.task.TaskBaseSpec;
import it.smartcommunitylabdhub.commons.services.ConfigurationService;
import it.smartcommunitylabdhub.commons.services.FunctionManager;
import it.smartcommunitylabdhub.commons.services.ModelManager;
import it.smartcommunitylabdhub.commons.services.SecretService;
import it.smartcommunitylabdhub.framework.k8s.base.K8sBaseRuntime;
import it.smartcommunitylabdhub.framework.k8s.model.K8sServiceInfo;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runtime.mlflow.specs.MlflowServeFunctionSpec;
import it.smartcommunitylabdhub.runtime.mlflow.specs.MlflowServeRunSpec;
import it.smartcommunitylabdhub.runtime.mlflow.specs.MlflowServeTaskSpec;
import it.smartcommunitylabdhub.runtime.modelserve.specs.ModelServeRunStatus;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
@RuntimeComponent(runtime = MlflowServeRuntime.RUNTIME)
public class MlflowServeRuntime
    extends K8sBaseRuntime<MlflowServeFunctionSpec, MlflowServeRunSpec, ModelServeRunStatus, K8sRunnable>
    implements InitializingBean {

    public static final String RUNTIME = "mlflowserve";
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

    @Value("${runtime.mlflowserve.image}")
    private String image;

    @Value("${runtime.mlflowserve.user-id}")
    private Integer userId;

    @Value("${runtime.mlflowserve.group-id}")
    private Integer groupId;

    public MlflowServeRuntime() {}

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.hasText(image, "image can not be null or empty");
        Assert.isTrue(image.startsWith(IMAGE), "image must be a version of " + IMAGE);
    }

    @Override
    public MlflowServeRunSpec build(@NotNull Executable function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!MlflowServeRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(
                        String.valueOf(run.getKind()),
                        MlflowServeRunSpec.KIND
                    )
            );
        }

        MlflowServeFunctionSpec funSpec = MlflowServeFunctionSpec.with(function.getSpec());
        MlflowServeRunSpec runSpec = MlflowServeRunSpec.with(run.getSpec());

        String kind = task.getKind();

        //build task spec as defined
        TaskBaseSpec taskSpec =
            switch (kind) {
                case MlflowServeTaskSpec.KIND -> {
                    yield MlflowServeTaskSpec.with(task.getSpec());
                }
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build run merging task spec overrides
        Map<String, Serializable> map = new HashMap<>();
        map.putAll(runSpec.toMap());
        taskSpec.toMap().forEach(map::putIfAbsent);

        MlflowServeRunSpec serveSpec = MlflowServeRunSpec.with(map);
        //ensure function is not modified
        serveSpec.setFunctionSpec(funSpec);

        return serveSpec;
    }

    @Override
    public K8sRunnable run(@NotNull Run run) {
        //check run kind
        if (!MlflowServeRunSpec.KIND.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(
                        String.valueOf(run.getKind()),
                        MlflowServeRunSpec.KIND
                    )
            );
        }

        MlflowServeRunSpec runSpec = MlflowServeRunSpec.with(run.getSpec());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        K8sRunnable runnable =
            switch (runAccessor.getTask()) {
                case MlflowServeTaskSpec.KIND -> new MlflowServeRunner(
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

    @Override
    public ModelServeRunStatus onRunning(@NotNull Run run, RunRunnable runnable) {
        ModelServeRunStatus status = ModelServeRunStatus.with(run.getStatus());

        MlflowServeFunctionSpec funSpec = MlflowServeFunctionSpec.with(run.getSpec());
        String modelName = StringUtils.hasText(funSpec.getModelName()) ? funSpec.getModelName() : "model";

        if (status.getService() != null && status.getService().getUrl() != null) {
            //add additional urls  for inference v2
            K8sServiceInfo service = status.getService();
            String baseUrl = service.getUrl() + "/v2";

            Set<String> urls = new HashSet<>();
            if (service.getUrls() != null) {
                urls.addAll(service.getUrls());
            }

            // Server Metadata
            urls.add(baseUrl + "/v2");

            // Model Metadata
            urls.add(baseUrl + "/v2/models/" + modelName);

            // Inference
            urls.add(baseUrl + "/v2/models/" + modelName + "/infer");

            service.setUrls(new ArrayList<>(urls));
            status.setService(service);
        }

        return status;
    }

    @Override
    public boolean isSupported(@NotNull Run run) {
        return MlflowServeRunSpec.KIND.equals(run.getKind());
    }
}
