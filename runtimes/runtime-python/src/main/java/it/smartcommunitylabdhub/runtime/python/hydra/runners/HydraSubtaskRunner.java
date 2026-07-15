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

package it.smartcommunitylabdhub.runtime.python.hydra.runners;

import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.python.PythonRuntime;
import it.smartcommunitylabdhub.runtime.python.build.PythonBaseRunner;
import it.smartcommunitylabdhub.runtime.python.config.PythonProperties;
import it.smartcommunitylabdhub.runtime.python.hydra.model.HydraSourceCode;
import it.smartcommunitylabdhub.runtime.python.hydra.specs.HydraFunctionSpec;
import it.smartcommunitylabdhub.runtime.python.hydra.specs.HydraSubtaskRunSpec;
import it.smartcommunitylabdhub.runtime.python.hydra.specs.HydraSubtaskTaskSpec;
import it.smartcommunitylabdhub.runtime.python.job.PythonJobTaskSpec;
import it.smartcommunitylabdhub.runtime.python.runners.PythonRunnerHelper;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
public class HydraSubtaskRunner extends PythonBaseRunner {

    private static ObjectMapper jsonMapper = JacksonMapper.CUSTOM_OBJECT_MAPPER;

    public HydraSubtaskRunner(
        PythonProperties properties,
        K8sBuilderHelper k8sBuilderHelper
    ) {
        super(properties, k8sBuilderHelper);

        //set handler for serve
        setHandlerTemplate(new ClassPathResource("runtime-hydra/docker/_subtask_handler.py"));
    }

    public K8sRunnable produce(Run run, Map<String, String> secretData) {
        HydraSubtaskRunSpec runSpec = new HydraSubtaskRunSpec(run.getSpec());
        HydraSubtaskTaskSpec taskSpec = runSpec.getTaskSubtaskSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(runSpec.getTaskSubtaskSpec().toMap());
        HydraFunctionSpec functionSpec = runSpec.getFunctionSpec();

        String pythonVersion = functionSpec.getPythonVersion().name();
        String userImage = functionSpec.getImage();
        String baseImage = functionSpec.getBaseImage();

        boolean standalone = !StringUtils.hasText(runSpec.getJobRef());

        try {
            //build base resources
            List<CoreEnv> coreEnvList = createEnvList(run, taskSpec);
            List<CoreEnv> coreSecrets = createSecrets(run, secretData);
            List<CoreVolume> coreVolumes = buildVolumes(run, taskSpec, pythonVersion, baseImage, userImage);

            String image = buildImage(pythonVersion, baseImage, userImage);
            List<String> requirements = properties.installDependencies()
                ? buildRequirements(image, functionSpec.getRequirements())
                : List.of();

            
            List<ContextSource> contextSources = new ArrayList<>();
            List<ContextRef> contextRefs = new ArrayList<>();

            //fetch source code
            HydraSourceCode sourceCode = functionSpec.getSource();
            //build nuclio definition
            HashMap<String, Serializable> event = new HashMap<>();
            event.put("body", jsonMapper.writeValueAsString(run));
            HashMap<String, Serializable> attributes = new HashMap<>(Map.of("event", event));
            HashMap<String, Serializable> triggers = new HashMap<>();
            HashMap<String, Serializable> job = new HashMap<>(Map.of("kind", "job", "attributes", attributes));
            triggers.put("job", job);

            String handlerRef = standalone ? "handler:handler" : "subtask_handler:handler";    
            String nuclioFunction = buildNuclioFunction(triggers, event, handlerRef);
            String handler = buildHandler(sourceCode);

            List<String> args = null;
            // it is expected that for non standalone runs the requirements and config are already present in the shared volume, 
            // so we skip the source and requirements injection for non-standalone runs
            if (standalone) {
                args = buildArgs(pythonVersion, baseImage, userImage);
                //read source and build context
                contextRefs.addAll(
                    PythonRunnerHelper.createContextRefs(sourceCode)
                );
                contextSources.addAll(
                    PythonRunnerHelper.createContextSources(entrypoint, handler, nuclioFunction, sourceCode, requirements)
                );
                //inject custom config to add our user
                contextSources.addAll(HydraRunnerHelper.createConfigSources(functionSpec.getConfig()));
                contextRefs.addAll(HydraRunnerHelper.createConfigRefs(functionSpec.getConfig()));

                //inject custom passwd to add our user
                if (passwdFile != null) {
                    ContextSource entry = ContextSource.builder()
                        .name("passwd")
                        .base64(Base64.getEncoder().encodeToString(passwdFile.getBytes(StandardCharsets.UTF_8)))
                        .mountPath("/etc/passwd")
                        .build();
                    contextSources.add(entry);
                }
            } else {
                String functionRef = "subtask_" + run.getId() + ".yaml";
                //write function file
                contextSources.add(
                    ContextSource
                        .builder()
                        .name(functionRef)
                        .base64(Base64.getEncoder().encodeToString(nuclioFunction.getBytes(StandardCharsets.UTF_8)))
                        .build()
                );
                args = buildSubtaskArgs(pythonVersion, baseImage, userImage, functionRef);
            }

            K8sJobRunnable k8sJobRunnable = K8sJobRunnable.builder()
                .runtime(PythonRuntime.RUNTIME)
                .task(PythonJobTaskSpec.KIND)
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
                .build();

            k8sJobRunnable.setId(run.getId());
            k8sJobRunnable.setProject(run.getProject());

            return k8sJobRunnable;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    protected List<String> buildSubtaskArgs(String pythonVersion, @Nullable String baseImage, @Nullable String userImage, String functionRef) {
        List<String> args = new ArrayList<>();
        if (useLayer(pythonVersion, baseImage, userImage)) {
            args.addAll(
                PythonRunnerHelper.buildEntrypointArgs(
                    homeDir,
                    command,
                    functionRef,
                    "/opt/nuclio/uv/uv",
                    List.of("/opt/nuclio/requirements/nuclio.txt", "/opt/nuclio/requirements/common.txt"),
                    "/opt/nuclio/whl"
                )
            );
        } else {
            args.addAll(PythonRunnerHelper.buildEntrypointArgs(homeDir, command, functionRef, null, null, null));
        }

        return args;
    }

    protected List<CoreVolume> createVolumes(Run run, K8sFunctionTaskBaseSpec taskSpec) {
        HydraSubtaskRunSpec runSpec = new HydraSubtaskRunSpec(run.getSpec());
        boolean standalone = !StringUtils.hasText(runSpec.getJobRef());
        if (!standalone) {
            // create shared volume
            return HydraRunnerHelper.createVolumes(run, taskSpec, properties.getVolumeSize(), k8sBuilderHelper, runSpec.getJobRef(), CoreVolume.VolumeType.shared_volume);
        }
        return super.createVolumes(run, taskSpec); 
    }

    
}
