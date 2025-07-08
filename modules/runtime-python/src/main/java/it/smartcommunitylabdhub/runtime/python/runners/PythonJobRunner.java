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
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCronJobRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runtime.python.PythonRuntime;
import it.smartcommunitylabdhub.runtime.python.model.NuclioFunctionBuilder;
import it.smartcommunitylabdhub.runtime.python.model.NuclioFunctionSpec;
import it.smartcommunitylabdhub.runtime.python.model.PythonSourceCode;
import it.smartcommunitylabdhub.runtime.python.specs.PythonFunctionSpec;
import it.smartcommunitylabdhub.runtime.python.specs.PythonJobTaskSpec;
import it.smartcommunitylabdhub.runtime.python.specs.PythonRunSpec;
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

    private static final int UID = 1000;
    private static final int GID = 100;

    private final String image;
    private final int userId;
    private final int groupId;
    private final String command;
    private final PythonFunctionSpec functionSpec;
    private final Map<String, String> secretData;

    private final K8sBuilderHelper k8sBuilderHelper;
    private final Resource entrypoint = new ClassPathResource("runtime-python/docker/entrypoint.sh");

    public PythonJobRunner(
        String image,
        Integer userId,
        Integer groupId,
        String command,
        PythonFunctionSpec functionPythonSpec,
        Map<String, String> secretData,
        K8sBuilderHelper k8sBuilderHelper
    ) {
        this.image = image;
        this.command = command;
        this.functionSpec = functionPythonSpec;
        this.secretData = secretData;
        this.k8sBuilderHelper = k8sBuilderHelper;

        this.userId = userId != null ? userId : UID;
        this.groupId = groupId != null ? groupId : GID;
    }

    public K8sRunnable produce(Run run) {
        PythonRunSpec runSpec = new PythonRunSpec(run.getSpec());
        PythonJobTaskSpec taskSpec = runSpec.getTaskJobSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());

        try {
            List<CoreEnv> coreEnvList = new ArrayList<>(
                List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
            );

            List<CoreEnv> coreSecrets = secretData == null
                ? null
                : secretData.entrySet().stream().map(e -> new CoreEnv(e.getKey(), e.getValue())).toList();

            Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

            //build nuclio definition
            HashMap<String, Serializable> event = new HashMap<>();

            event.put("body", jsonMapper.writeValueAsString(run));

            NuclioFunctionSpec nuclio = NuclioFunctionSpec
                .builder()
                .runtime("python")
                //invoke user code wrapped via default handler
                .handler("run_handler:handler_job")
                //directly invoke user code
                // .handler("main:" + runSpec.getFunctionSpec().getSource().getHandler())
                .event(event)
                .build();

            String nuclioFunction = NuclioFunctionBuilder.write(nuclio);

            //read source and build context
            List<ContextRef> contextRefs = null;
            List<ContextSource> contextSources = new ArrayList<>();

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

            //function definition
            ContextSource fn = ContextSource
                .builder()
                .name("function.yaml")
                .base64(Base64.getEncoder().encodeToString(nuclioFunction.getBytes(StandardCharsets.UTF_8)))
                .build();
            contextSources.add(fn);

            //source
            if (functionSpec.getSource() != null) {
                PythonSourceCode source = functionSpec.getSource();
                String path = "main.py";

                if (StringUtils.hasText(source.getSource())) {
                    try {
                        //evaluate if local path (no scheme)
                        UriComponents uri = UriComponentsBuilder.fromUriString(source.getSource()).build();
                        String scheme = uri.getScheme();

                        if (scheme != null) {
                            //write as ref
                            contextRefs = Collections.singletonList(ContextRef.from(source.getSource()));
                        } else {
                            if (StringUtils.hasText(path)) {
                                //override path for local src
                                path = uri.getPath();
                                if (path.startsWith(".")) {
                                    path = path.substring(1);
                                }
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        //skip invalid source
                    }
                }

                if (StringUtils.hasText(source.getBase64())) {
                    contextSources.add(ContextSource.builder().name(path).base64(source.getBase64()).build());
                }
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

            // requirements.txt
            if (functionSpec.getRequirements() != null && !functionSpec.getRequirements().isEmpty()) {
                //write as file
                String content = String.join("\n", functionSpec.getRequirements());
                contextSources.add(
                    ContextSource
                        .builder()
                        .name("requirements.txt")
                        .base64(Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)))
                        .build()
                );
            }

            //merge env with PYTHON path override
            coreEnvList.add(new CoreEnv("PYTHONPATH", "${PYTHONPATH}:/shared/"));

            K8sRunnable k8sJobRunnable = K8sJobRunnable
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
                .resources(taskSpec.getResources())
                .volumes(taskSpec.getVolumes())
                .nodeSelector(taskSpec.getNodeSelector())
                .affinity(taskSpec.getAffinity())
                .tolerations(taskSpec.getTolerations())
                .runtimeClass(taskSpec.getRuntimeClass())
                .priorityClass(taskSpec.getPriorityClass())
                .template(taskSpec.getProfile())
                //securityContext
                .fsGroup(groupId)
                .runAsGroup(groupId)
                .runAsUser(userId)
                .build();

            if (StringUtils.hasText(taskSpec.getSchedule())) {
                //build a cronJob
                k8sJobRunnable =
                    K8sCronJobRunnable
                        .builder()
                        .runtime(PythonRuntime.RUNTIME)
                        .task(PythonJobTaskSpec.KIND)
                        .state(State.READY.name())
                        .labels(
                            k8sBuilderHelper != null
                                ? List.of(
                                    new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction())
                                )
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
                        .resources(taskSpec.getResources())
                        .volumes(taskSpec.getVolumes())
                        .nodeSelector(taskSpec.getNodeSelector())
                        .affinity(taskSpec.getAffinity())
                        .tolerations(taskSpec.getTolerations())
                        .runtimeClass(taskSpec.getRuntimeClass())
                        .priorityClass(taskSpec.getPriorityClass())
                        .template(taskSpec.getProfile())
                        //securityContext
                        .fsGroup(groupId)
                        .runAsGroup(groupId)
                        .runAsUser(userId)
                        //specific
                        .schedule(taskSpec.getSchedule())
                        .build();
            }

            k8sJobRunnable.setId(run.getId());
            k8sJobRunnable.setProject(run.getProject());

            return k8sJobRunnable;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }
}
