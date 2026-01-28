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

package it.smartcommunitylabdhub.runtime.python.runners;

import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CorePort;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreResource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sServeRunnable;
import it.smartcommunitylabdhub.functions.FunctionManager;
import it.smartcommunitylabdhub.runtime.python.PythonRuntime;
import it.smartcommunitylabdhub.runtime.python.specs.PythonFunctionSpec;
import it.smartcommunitylabdhub.runtime.python.specs.PythonServeRunSpec;
import it.smartcommunitylabdhub.runtime.python.specs.PythonServeTaskSpec;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

public class PythonServeRunner {

    private static final int UID = 8877;
    private static final int GID = 999;
    private static final int HTTP_PORT = 8080;

    private final Map<String, String> images;
    private final Map<String, String> serverlessImages;
    private final Map<String, String> baseImages;
    private final String volumeSizeSpec;

    private final int userId;
    private final int groupId;
    private final String command;
    private final List<String> dependencies;

    private final K8sBuilderHelper k8sBuilderHelper;
    private final FunctionManager functionService;

    private final Resource entrypoint = new ClassPathResource("runtime-python/docker/entrypoint.sh");

    public PythonServeRunner(
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
        this.images = images;
        this.serverlessImages = serverlessImages;
        this.baseImages = baseImages;
        this.command = command;

        this.k8sBuilderHelper = k8sBuilderHelper;
        this.functionService = functionService;

        this.userId = userId != null ? userId : UID;
        this.groupId = groupId != null ? groupId : GID;
        this.dependencies = dependencies;
        this.volumeSizeSpec = volumeSizeSpec;
    }
    public K8sRunnable produce(Run run, Map<String, String> secretData) {
        PythonServeRunSpec runSpec = new PythonServeRunSpec(run.getSpec());
        PythonServeTaskSpec taskSpec = runSpec.getTaskServeSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());
        PythonFunctionSpec functionSpec = runSpec.getFunctionSpec();

        List<CoreEnv> coreEnvList = PythonRunnerHelper.createEnvList(run, taskSpec);
        List<CoreEnv> coreSecrets = PythonRunnerHelper.createSecrets(secretData);

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

        //define http trigger
        HashMap<String, Serializable> triggers = new HashMap<>();
        HashMap<String, Serializable> http = new HashMap<>(Map.of("kind", "http", "maxWorkers", 2));
        triggers.put("http", http);

        //read source and build context
        List<ContextRef> contextRefs = PythonRunnerHelper.createContextRefs(functionSpec);
        List<ContextSource> contextSources = PythonRunnerHelper.createContextSources(
            functionSpec,
            null,
            triggers,
            "_serve_handler",
            dependencies
        );

        //write entrypoint
        try {
            ContextSource entry = ContextSource
                .builder()
                .name("entrypoint.sh")
                .base64(Base64.getEncoder().encodeToString(entrypoint.getContentAsByteArray()))
                .build();
            contextSources.add(entry);
        } catch (IOException ioe) {
            throw new CoreRuntimeException("error with reading entrypoint for runtime-python");
        }

        //expose http trigger only
        CorePort servicePort = new CorePort(HTTP_PORT, HTTP_PORT);

        //evaluate service names
        List<String> serviceNames = new ArrayList<>();
        if (taskSpec.getServiceName() != null && StringUtils.hasText(taskSpec.getServiceName())) {
            //prepend with function name
            serviceNames.add(taskAccessor.getFunction() + "-" + taskSpec.getServiceName());
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
            .runtime(PythonRuntime.RUNTIME)
            .task(PythonServeTaskSpec.KIND)
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
            .resources(k8sBuilderHelper != null ? k8sBuilderHelper.convertResources(taskSpec.getResources()) : null)
            .volumes(coreVolumes)
            .template(taskSpec.getProfile())
            //securityContext
            .fsGroup(groupId)
            .runAsGroup(groupId)
            .runAsUser(userId)
            //specific
            .replicas(taskSpec.getReplicas())
            .servicePorts(List.of(servicePort))
            .serviceType(taskSpec.getServiceType())
            .serviceNames(serviceNames != null && !serviceNames.isEmpty() ? serviceNames : null)
            .build();

        k8sServeRunnable.setId(run.getId());
        k8sServeRunnable.setProject(run.getProject());

        return k8sServeRunnable;
    }
}
