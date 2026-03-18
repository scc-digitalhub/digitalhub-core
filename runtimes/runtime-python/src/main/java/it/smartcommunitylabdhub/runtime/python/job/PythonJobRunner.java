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

package it.smartcommunitylabdhub.runtime.python.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
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
import it.smartcommunitylabdhub.runtime.python.build.PythonBaseRunner;
import it.smartcommunitylabdhub.runtime.python.config.PythonProperties;
import it.smartcommunitylabdhub.runtime.python.model.PythonSourceCode;
import it.smartcommunitylabdhub.runtime.python.runners.PythonRunnerHelper;
import it.smartcommunitylabdhub.runtime.python.specs.PythonFunctionSpec;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

@Slf4j
public class PythonJobRunner extends PythonBaseRunner {

    private static ObjectMapper jsonMapper = JacksonMapper.CUSTOM_OBJECT_MAPPER;

    public PythonJobRunner(PythonProperties properties, K8sBuilderHelper k8sBuilderHelper) {
        super(properties, k8sBuilderHelper);
        //set handler for job
        setHandlerTemplate(new ClassPathResource("runtime-python/docker/_job_handler.py"));
    }

    public K8sJobRunnable produce(Run run, Map<String, String> secretData) {
        PythonJobRunSpec runSpec = new PythonJobRunSpec(run.getSpec());
        PythonJobTaskSpec taskSpec = runSpec.getTaskJobSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());
        PythonFunctionSpec functionSpec = runSpec.getFunctionSpec();

        String pythonVersion = functionSpec.getPythonVersion().name();
        String userImage = functionSpec.getImage();
        String baseImage = functionSpec.getBaseImage();

        try {
            //build base resources
            List<CoreEnv> coreEnvList = createEnvList(run, taskSpec);
            List<CoreEnv> coreSecrets = createSecrets(run, secretData);
            List<CoreVolume> coreVolumes = buildVolumes(run, taskSpec, pythonVersion, baseImage, userImage);

            List<String> args = buildArgs(pythonVersion, baseImage, userImage);
            String image = buildImage(pythonVersion, baseImage, userImage);
            List<String> requirements = buildRequirements(image, functionSpec.getRequirements());

            //fetch source code
            PythonSourceCode sourceCode = functionSpec.getSource();

            //build nuclio definition
            HashMap<String, Serializable> event = new HashMap<>();
            event.put("body", jsonMapper.writeValueAsString(run));
            HashMap<String, Serializable> attributes = new HashMap<>(Map.of("event", event));
            HashMap<String, Serializable> triggers = new HashMap<>();
            HashMap<String, Serializable> job = new HashMap<>(Map.of("kind", "job", "attributes", attributes));
            triggers.put("job", job);

            String nuclioFunction = buildNuclioFunction(triggers, event);
            String handler = buildHandler(sourceCode);

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
}
