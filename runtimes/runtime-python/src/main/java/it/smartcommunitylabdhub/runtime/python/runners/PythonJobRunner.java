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
import it.smartcommunitylabdhub.runtime.python.model.NuclioFunctionBuilder;
import it.smartcommunitylabdhub.runtime.python.model.NuclioFunctionSpec;
import it.smartcommunitylabdhub.runtime.python.model.PythonSourceCode;
import it.smartcommunitylabdhub.runtime.python.specs.PythonFunctionSpec;
import it.smartcommunitylabdhub.runtime.python.specs.PythonJobRunSpec;
import it.smartcommunitylabdhub.runtime.python.specs.PythonJobTaskSpec;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

public class PythonJobRunner {

    private static ObjectMapper jsonMapper = JacksonMapper.CUSTOM_OBJECT_MAPPER;

    private static final int UID = 8877;
    private static final int GID = 999;

    private final Map<String, String> images;
    private final int userId;
    private final int groupId;
    private final String command;

    private final K8sBuilderHelper k8sBuilderHelper;
    private final Resource entrypoint = new ClassPathResource("runtime-python/docker/entrypoint.sh");

    public PythonJobRunner(
        Map<String, String> images,
        Integer userId,
        Integer groupId,
        String command,
        K8sBuilderHelper k8sBuilderHelper
    ) {
        this.images = images;
        this.command = command;

        this.k8sBuilderHelper = k8sBuilderHelper;

        this.userId = userId != null ? userId : UID;
        this.groupId = groupId != null ? groupId : GID;
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
                "_job_handler"
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

            List<String> args = new ArrayList<>(
                List.of(
                    "/shared/entrypoint.sh",
                    "--processor",
                    command,
                    "--config",
                    "/shared/function.yaml",
                    "--requirements",
                    "/shared/requirements.txt"
                )
            );

        
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
