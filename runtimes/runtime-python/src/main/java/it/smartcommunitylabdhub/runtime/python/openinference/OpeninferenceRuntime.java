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

package it.smartcommunitylabdhub.runtime.python.openinference;

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
import it.smartcommunitylabdhub.framework.k8s.model.K8sServiceInfo;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sContainerBuilderRunnable;
import it.smartcommunitylabdhub.functions.FunctionManager;
import it.smartcommunitylabdhub.runtime.python.config.PythonProperties;
import it.smartcommunitylabdhub.runtime.python.openinference.model.InferenceV2Service;
import it.smartcommunitylabdhub.runtime.python.openinference.runners.OpeninferenceBuildRunner;
import it.smartcommunitylabdhub.runtime.python.openinference.runners.OpeninferenceServeRunner;
import it.smartcommunitylabdhub.runtime.python.openinference.specs.OpeninferenceBuildRunSpec;
import it.smartcommunitylabdhub.runtime.python.openinference.specs.OpeninferenceBuildTaskSpec;
import it.smartcommunitylabdhub.runtime.python.openinference.specs.OpeninferenceFunctionSpec;
import it.smartcommunitylabdhub.runtime.python.openinference.specs.OpeninferenceRunSpec;
import it.smartcommunitylabdhub.runtime.python.openinference.specs.OpeninferenceRunStatus;
import it.smartcommunitylabdhub.runtime.python.openinference.specs.OpeninferenceServeRunSpec;
import it.smartcommunitylabdhub.runtime.python.openinference.specs.OpeninferenceServeTaskSpec;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.Assert;

@Slf4j
@RuntimeComponent(runtime = OpeninferenceRuntime.RUNTIME)
public class OpeninferenceRuntime
    extends K8sFunctionBaseRuntime<OpeninferenceFunctionSpec, OpeninferenceRunSpec, OpeninferenceRunStatus, K8sRunnable>
    implements InitializingBean {

    public static final int HTTP_PORT = 8080;
    public static final int GRPC_PORT = 9000;
    public static final int UID = 8877;
    public static final int GID = 999;
    public static final String HOME_DIR = "/home/openinference";

    public static final String RUNTIME = "openinference";
    public static final String[] KINDS = { OpeninferenceServeRunSpec.KIND, OpeninferenceBuildRunSpec.KIND };

    private final PythonProperties properties;

    private OpeninferenceBuildRunner buildRunner;
    private OpeninferenceServeRunner serveRunner;

    @Autowired
    private SecretService secretService;

    @Autowired
    private FunctionManager functionService;

    @Autowired
    private CredentialsService credentialsService;

    @Autowired
    private ConfigurationService configurationService;

    public OpeninferenceRuntime(@Qualifier("openinferenceProperties") PythonProperties properties) {
        Assert.notNull(properties, "properties are required");
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.buildRunner = new OpeninferenceBuildRunner(properties, k8sBuilderHelper);
        this.serveRunner = new OpeninferenceServeRunner(properties, k8sBuilderHelper, functionService);
    }

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
                case OpeninferenceServeTaskSpec.KIND -> serveRunner.produce(run, secrets);
                case OpeninferenceBuildTaskSpec.KIND -> buildRunner.produce(run, secrets);
                default -> throw new IllegalArgumentException("Kind not recognized. Cannot retrieve the right Runner");
            };

        //extract auth from security context to inflate secured credentials
        UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
        if (auth != null) {
            //get credentials from providers
            List<Credentials> credentials = credentialsService.getCredentials(auth);
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

    @Override
    public OpeninferenceRunStatus onRunning(@NotNull Run run, RunRunnable runnable) {
        OpeninferenceRunStatus status = OpeninferenceRunStatus.with(run.getStatus());

        OpeninferenceFunctionSpec funSpec = OpeninferenceFunctionSpec.with(run.getSpec());

        if (status.getService() != null && status.getService().getUrl() != null) {
            //add additional urls  for inference v2
            K8sServiceInfo service = status.getService();
            String baseUrl = service.getUrl();

            Set<String> urls = new HashSet<>();
            if (service.getUrls() != null) {
                urls.addAll(service.getUrls());
            }

            // Server Metadata
            urls.add(baseUrl + "/v2");

            // Model Metadata
            urls.add(baseUrl + "/v2/models/" + funSpec.getModelName());

            // Inference
            urls.add(baseUrl + "/v2/models/" + funSpec.getModelName() + "/infer");

            service.setUrls(new ArrayList<>(urls));
            status.setService(service);

            //add inference specific info
            InferenceV2Service inferenceService = new InferenceV2Service();
            inferenceService.setBaseUrl(baseUrl);

            inferenceService.setModel(funSpec.getModelName());
            inferenceService.setInferenceUrl(baseUrl + "/v2/models/" + funSpec.getModelName() + "/infer");
            inferenceService.setModelMetadataUrl(baseUrl + "/v2/models/" + funSpec.getModelName());
            inferenceService.setModelReadinessUrl(baseUrl + "/v2/models/" + funSpec.getModelName() + "/ready");
            inferenceService.setReadinessUrl(baseUrl + "/v2/health/ready");
            inferenceService.setLivenessUrl(baseUrl + "/v2/health/live");
            //TODO
            // inferenceService.setStatus(service.getStatus());
            status.setInferenceV2(inferenceService);
        }

        return status;
    }
}
