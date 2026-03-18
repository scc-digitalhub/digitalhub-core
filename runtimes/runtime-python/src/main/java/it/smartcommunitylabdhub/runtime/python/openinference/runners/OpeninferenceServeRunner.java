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

package it.smartcommunitylabdhub.runtime.python.openinference.runners;

import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CorePort;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sServeRunnable;
import it.smartcommunitylabdhub.functions.FunctionManager;
import it.smartcommunitylabdhub.runtime.python.build.PythonBaseRunner;
import it.smartcommunitylabdhub.runtime.python.config.PythonProperties;
import it.smartcommunitylabdhub.runtime.python.model.PythonSourceCode;
import it.smartcommunitylabdhub.runtime.python.openinference.OpeninferenceRuntime;
import it.smartcommunitylabdhub.runtime.python.openinference.specs.OpeninferenceFunctionSpec;
import it.smartcommunitylabdhub.runtime.python.openinference.specs.OpeninferenceServeRunSpec;
import it.smartcommunitylabdhub.runtime.python.openinference.specs.OpeninferenceServeTaskSpec;
import it.smartcommunitylabdhub.runtime.python.runners.PythonRunnerHelper;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;

public class OpeninferenceServeRunner extends PythonBaseRunner {

    private final FunctionManager functionService;

    public OpeninferenceServeRunner(
        PythonProperties properties,
        K8sBuilderHelper k8sBuilderHelper,
        FunctionManager functionService
    ) {
        super(properties, k8sBuilderHelper);
        this.functionService = functionService;

        //set handler for serve
        setHandlerTemplate(new ClassPathResource("runtime-openinference/docker/openinference-handler.py"));
    }

    public K8sRunnable produce(Run run, Map<String, String> secretData) {
        OpeninferenceServeRunSpec runSpec = new OpeninferenceServeRunSpec(run.getSpec());
        OpeninferenceServeTaskSpec taskSpec = runSpec.getTaskServeSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(runSpec.getTaskServeSpec().toMap());
        OpeninferenceFunctionSpec functionSpec = runSpec.getFunctionSpec();

        String pythonVersion = functionSpec.getPythonVersion().name();
        String userImage = functionSpec.getImage();
        String baseImage = functionSpec.getBaseImage();

        //build base resources
        List<CoreEnv> coreEnvList = createEnvList(run, taskSpec);
        List<CoreEnv> coreSecrets = createSecrets(run, secretData);
        List<CoreVolume> coreVolumes = buildVolumes(run, taskSpec, pythonVersion, baseImage, userImage);

        List<String> args = buildArgs(pythonVersion, baseImage, userImage);
        String image = buildImage(pythonVersion, baseImage, userImage);

        //fetch source code
        PythonSourceCode sourceCode = functionSpec.getSource();

        //define trigger
        HashMap<String, Serializable> triggers = OpeninferenceContextBuilder.buildTriggers(run, functionSpec);

        //build nuclio definition
        String nuclioFunction = buildNuclioFunction(triggers, null);
        String handler = buildHandler(sourceCode);

        //requirements
        List<String> requirements = buildRequirements(image, functionSpec.getRequirements());

        //read source and build context
        List<ContextRef> contextRefs = PythonRunnerHelper.createContextRefs(sourceCode);
        List<ContextSource> contextSources = new ArrayList<>(
            PythonRunnerHelper.createContextSources(entrypoint, handler, nuclioFunction, sourceCode, requirements)
        );

        //inject custom passwd to add our user
        if (passwdFile != null) {
            ContextSource entry = ContextSource
                .builder()
                .name("passwd")
                .base64(Base64.getEncoder().encodeToString(passwdFile.getBytes(StandardCharsets.UTF_8)))
                .mountPath("/etc/passwd")
                .build();
            contextSources.add(entry);
        }

        //evaluate service names
        List<String> serviceNames = new ArrayList<>();
        if (
            runSpec.getTaskServeSpec().getServiceName() != null &&
            StringUtils.hasText(runSpec.getTaskServeSpec().getServiceName())
        ) {
            //prepend with function name
            serviceNames.add(taskAccessor.getFunction() + "-" + runSpec.getTaskServeSpec().getServiceName());
        }

        if (functionService != null) {
            //check if latest
            Function latest = functionService.getLatestFunction(run.getProject(), taskAccessor.getFunction());
            if (taskAccessor.getFunctionId().equals(latest.getId())) {
                //prepend with function name
                serviceNames.add(taskAccessor.getFunction() + "-latest");
            }
        }

        K8sRunnable k8sServeRunnable = K8sServeRunnable
            .builder()
            .runtime(OpeninferenceRuntime.RUNTIME)
            .task(OpeninferenceServeTaskSpec.KIND)
            .state(State.READY.name())
            .labels(
                k8sBuilderHelper != null
                    ? List.of(new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction()))
                    : null
            )
            //base
            .image(image)
            .command("/bin/bash")
            .args(args.toArray(new String[0]))
            .contextRefs(contextRefs)
            .contextSources(contextSources)
            .envs(coreEnvList)
            .secrets(coreSecrets)
            .resources(
                k8sBuilderHelper != null
                    ? k8sBuilderHelper.convertResources(runSpec.getTaskServeSpec().getResources())
                    : null
            )
            .volumes(coreVolumes)
            .template(taskSpec.getProfile())
            //securityContext
            .fsGroup(groupId)
            .runAsGroup(groupId)
            .runAsUser(userId)
            //specific
            .replicas(taskSpec.getReplicas())
            // http and grpc ports
            .servicePorts(
                List.of(
                    new CorePort(OpeninferenceRuntime.HTTP_PORT, OpeninferenceRuntime.HTTP_PORT),
                    new CorePort(OpeninferenceRuntime.GRPC_PORT, OpeninferenceRuntime.GRPC_PORT)
                )
            )
            .serviceType(taskSpec.getServiceType())
            .serviceNames(serviceNames != null && !serviceNames.isEmpty() ? serviceNames : null)
            .build();

        k8sServeRunnable.setId(run.getId());
        k8sServeRunnable.setProject(run.getProject());

        return k8sServeRunnable;
    }
}
