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

package it.smartcommunitylabdhub.runtime.openinference.runners;

import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CorePort;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreResource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sServeRunnable;
import it.smartcommunitylabdhub.functions.FunctionManager;
import it.smartcommunitylabdhub.runtime.openinference.OpeninferenceRuntime;
import it.smartcommunitylabdhub.runtime.openinference.specs.OpeninferenceFunctionSpec;
import it.smartcommunitylabdhub.runtime.openinference.specs.OpeninferenceServeRunSpec;
import it.smartcommunitylabdhub.runtime.openinference.specs.OpeninferenceServeTaskSpec;
import it.smartcommunitylabdhub.runtime.python.runners.PythonRunnerHelper;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

public class OpeninferenceServeRunner extends OpeninferenceBaseRunner {

    private static final int UID = 8877;
    private static final int GID = 999;

    private final int userId;
    private final int groupId;

    private final String volumeSizeSpec;

    private final FunctionManager functionService;

    private final Resource entrypoint = new ClassPathResource("runtime-python/docker/entrypoint.sh");
    
    public OpeninferenceServeRunner(
        Map<String, String> images,
        Map<String, String> serverlessImages,
        Map<String, String> baseImages,
        String volumeSizeSpec,
        Integer userId,
        Integer groupId,
        String command,
        K8sBuilderHelper k8sBuilderHelper,
        FunctionManager functionService,
        List<String> dependencies
    ) {
        super(images, serverlessImages, baseImages, command, k8sBuilderHelper, dependencies);
        this.functionService = functionService;
        this.volumeSizeSpec = volumeSizeSpec;

        this.userId = userId != null ? userId : UID;
        this.groupId = groupId != null ? groupId : GID;
        try {
            this.handlerMustache = mustacheFactory.compile(new InputStreamReader( handlerTemplate.getInputStream()), "openinference-handler");
        } catch (IOException ioe) {
            throw new CoreRuntimeException("error with reading handler template for runtime-openinference");
        }
    }

    public K8sRunnable produce(Run run, Map<String, String> secretData) {
        
        OpeninferenceServeRunSpec runSpec = new OpeninferenceServeRunSpec(run.getSpec());
        OpeninferenceServeTaskSpec taskSpec = runSpec.getTaskServeSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(runSpec.getTaskServeSpec().toMap());
        OpeninferenceFunctionSpec functionSpec = runSpec.getFunctionSpec();
        
        //prepare context
        Context ctx = prepareContext(run, secretData, runSpec, runSpec.getTaskServeSpec(), runSpec.getFunctionSpec());  

        List<CoreVolume> coreVolumes = new ArrayList<>(
            taskSpec.getVolumes() != null ? taskSpec.getVolumes() : List.of()
        );
        //check if scratch disk is requested as resource or set default
        String volumeSize = taskSpec.getResources() != null && taskSpec.getResources().getDisk() != null
            ? taskSpec.getResources().getDisk()
            : volumeSizeSpec;
        CoreResource diskResource = new CoreResource();
        diskResource.setDisk(volumeSize);
        Optional
            .ofNullable(k8sBuilderHelper)
            .ifPresent(helper -> {
                Optional.ofNullable(helper.buildSharedVolume(diskResource)).ifPresent(coreVolumes::add);
            });
        //write entrypoint
        try {
            ContextSource entry = ContextSource
                .builder()
                .name("entrypoint.sh")
                .base64(Base64.getEncoder().encodeToString(entrypoint.getContentAsByteArray()))
                .build();
            ctx.contextSources().add(entry);
        } catch (IOException ioe) {
            throw new CoreRuntimeException("error with reading entrypoint for runtime-guardrail");
        }

        List<String> args = new ArrayList<>();
        String layerImage  = serverlessImages.get(functionSpec.getPythonVersion().name());
        String defaultImage = images.get(functionSpec.getPythonVersion().name());
        String defaultBaseImage = baseImages.get(functionSpec.getPythonVersion().name());
        
        String userImage = functionSpec.getImage();
        String baseImage = functionSpec.getBaseImage();

        if (!StringUtils.hasText(baseImage) && !StringUtils.hasText(userImage) && !StringUtils.hasText(defaultImage) && !StringUtils.hasText(defaultBaseImage)) {
            throw new IllegalArgumentException("No suitable image configuration found");
        }

        String image = null;
        
        // use layer image if no predefined image is set and user set base image or there is no default image defined 
        boolean useLayer = !StringUtils.hasText(userImage) && (StringUtils.hasText(baseImage) || !StringUtils.hasText(defaultImage));
        // In this case 

        if (useLayer) {
            // - assume dependencies from wheel 
            // - mount image with processor and wheel
            // - install dependencies at entrypoint
            args.addAll(PythonRunnerHelper.buildArgs("/opt/nuclio/processor", "/opt/nuclio/uv/uv", "/opt/nuclio/requirements/common.txt", "/opt/nuclio/pywhl"));
            coreVolumes.add(PythonRunnerHelper.createServerlessImageVolume(layerImage));
            image = StringUtils.hasText(baseImage) ? baseImage : defaultBaseImage;
        } else {
            // use the image as is
            args.addAll(PythonRunnerHelper.buildArgs(command, null, null, null));
            image = StringUtils.hasText(userImage) ? userImage :  defaultImage;
        }

        //evaluate service names
        List<String> serviceNames = new ArrayList<>();
        if (runSpec.getTaskServeSpec().getServiceName() != null && StringUtils.hasText(runSpec.getTaskServeSpec().getServiceName())) {
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
            .contextRefs(ctx.contextRefs())
            .contextSources(ctx.contextSources())
            .envs(ctx.coreEnvList())
            .secrets(ctx.coreSecrets())
            .resources(k8sBuilderHelper != null ? k8sBuilderHelper.convertResources(runSpec.getTaskServeSpec().getResources()) : null)
            .volumes(coreVolumes)
            .template(taskSpec.getProfile())
            //securityContext
            .fsGroup(groupId)
            .runAsGroup(groupId)
            .runAsUser(userId)
            //specific
            .replicas(taskSpec.getReplicas())
            // http and grpc ports
             .servicePorts(List.of(new CorePort(HTTP_PORT, HTTP_PORT), new CorePort(GRPC_PORT, GRPC_PORT)))
            .serviceType(taskSpec.getServiceType())
            .serviceNames(serviceNames != null && !serviceNames.isEmpty() ? serviceNames : null)
            .build();

        k8sServeRunnable.setId(run.getId());
        k8sServeRunnable.setProject(run.getProject());

        return k8sServeRunnable;
    }
}

