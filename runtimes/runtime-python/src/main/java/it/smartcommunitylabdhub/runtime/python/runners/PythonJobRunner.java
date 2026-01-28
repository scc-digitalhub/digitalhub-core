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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.runtime.python.PythonRuntime;
import it.smartcommunitylabdhub.runtime.python.specs.PythonFunctionSpec;
import it.smartcommunitylabdhub.runtime.python.specs.PythonJobRunSpec;
import it.smartcommunitylabdhub.runtime.python.specs.PythonJobTaskSpec;
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

public class PythonJobRunner {

    private static ObjectMapper jsonMapper = JacksonMapper.CUSTOM_OBJECT_MAPPER;

    private static final int UID = 8877;
    private static final int GID = 999;

    private final Map<String, String> images;
    private final Map<String, String> serverlessImages;
    private final int userId;
    private final int groupId;
    private final String command;
    private final List<String> dependencies;

    private final K8sBuilderHelper k8sBuilderHelper;
    private final Resource entrypoint = new ClassPathResource("runtime-python/docker/entrypoint.sh");

    public PythonJobRunner(
        Map<String, String> images,
        Map<String, String> serverlessImages,
        Integer userId,
        Integer groupId,
        String command,
        K8sBuilderHelper k8sBuilderHelper,
        List<String> dependencies
    ) {
        this.images = images;
        this.serverlessImages = serverlessImages;
        this.command = command;

        this.k8sBuilderHelper = k8sBuilderHelper;

        this.userId = userId != null ? userId : UID;
        this.groupId = groupId != null ? groupId : GID;
        this.dependencies = dependencies;
    }

    public K8sJobRunnable produce(Run run, Map<String, String> secretData) {
        PythonJobRunSpec runSpec = new PythonJobRunSpec(run.getSpec());
        PythonJobTaskSpec taskSpec = runSpec.getTaskJobSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());
        PythonFunctionSpec functionSpec = runSpec.getFunctionSpec();

        try {

            List<CoreEnv> coreEnvList = PythonRunnerHelper.createEnvList(run, taskSpec);
            List<CoreEnv> coreSecrets = PythonRunnerHelper.createSecrets(secretData);

            List<CoreVolume> coreVolumes = new ArrayList<>(
                taskSpec.getVolumes() != null ? taskSpec.getVolumes() : List.of()
            );
            List<String> args = new ArrayList<>();

            // check serverless image layer exists. In this case 
            // - assume dependencies from wheel 
            // - mount image with processor and wheel
            // - install dependencies at entrypoint
            String serverlessImage  = functionSpec.getPythonVersion() != null
                ? serverlessImages.get(functionSpec.getPythonVersion().name())
                : null;

            if (serverlessImage != null && StringUtils.hasText(serverlessImage)) {
                args.addAll(PythonRunnerHelper.buildArgs("/opt/nuclio/processor", "/opt/nuclio/uv/uv", "/opt/nuclio/requirements/common.txt", "/opt/nuclio/pywhl"));
                coreVolumes.add(PythonRunnerHelper.createServerlessImageVolume(serverlessImage));
            } else {
                args.addAll(PythonRunnerHelper.buildArgs(command, null, null, null));
            }

            //check if scratch disk is requested as resource
            Optional
                .ofNullable(k8sBuilderHelper)
                .flatMap(helper -> Optional.ofNullable(taskSpec.getResources()))
                .filter(resources -> resources.getDisk() != null)
                .ifPresent(resources -> {
                    Optional.ofNullable(k8sBuilderHelper.buildSharedVolume(resources)).ifPresent(coreVolumes::add);
                });

            //build nuclio definition
            HashMap<String, Serializable> event = new HashMap<>();
            event.put("body", jsonMapper.writeValueAsString(run));

            //read source and build context
            List<ContextRef> contextRefs = PythonRunnerHelper.createContextRefs(functionSpec);
            List<ContextSource> contextSources = PythonRunnerHelper.createContextSources(
                functionSpec,
                event,
                null,
                "_job_handler",
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
        
            String image = images.get(functionSpec.getPythonVersion().name());

            K8sJobRunnable k8sJobRunnable = K8sJobRunnable
                .builder()
                .runtime(PythonRuntime.RUNTIME)
                .task(PythonJobTaskSpec.KIND)
                .state(State.READY.name())
                .labels(
                    k8sBuilderHelper != null
                        ? List.of(new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction()))
                        : null
                )
                //base
                .image(StringUtils.hasText(functionSpec.getImage()) ? functionSpec.getImage() : image)
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
                .build();

            k8sJobRunnable.setId(run.getId());
            k8sJobRunnable.setProject(run.getProject());

            return k8sJobRunnable;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }
}
