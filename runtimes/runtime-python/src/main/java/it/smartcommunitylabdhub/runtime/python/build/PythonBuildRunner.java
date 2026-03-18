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

package it.smartcommunitylabdhub.runtime.python.build;

import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sContainerBuilderRunnable;
import it.smartcommunitylabdhub.runtime.python.PythonRuntime;
import it.smartcommunitylabdhub.runtime.python.config.PythonProperties;
import it.smartcommunitylabdhub.runtime.python.model.PythonSourceCode;
import it.smartcommunitylabdhub.runtime.python.runners.PythonRunnerHelper;
import it.smartcommunitylabdhub.runtime.python.specs.PythonFunctionSpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;

@Slf4j
public class PythonBuildRunner extends PythonBaseBuildRunner {

    public static final int MIN_IMAGE_NAME_LENGTH = 3;

    public PythonBuildRunner(PythonProperties properties, K8sBuilderHelper k8sBuilderHelper) {
        super(properties, k8sBuilderHelper);
        //set handler for job by default
        setHandlerTemplate(new ClassPathResource("runtime-python/docker/_job_handler.py"));
    }

    public K8sContainerBuilderRunnable produce(Run run, Map<String, String> secretData) {
        PythonBuildRunSpec runSpec = new PythonBuildRunSpec(run.getSpec());
        PythonBuildTaskSpec taskSpec = runSpec.getTaskBuildSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());
        PythonFunctionSpec functionSpec = runSpec.getFunctionSpec();

        String pythonVersion = functionSpec.getPythonVersion().name();
        String baseImage = functionSpec.getBaseImage();

        //build base resources
        List<CoreEnv> coreEnvList = createEnvList(run, taskSpec);
        List<CoreEnv> coreSecrets = createSecrets(run, secretData);

        //fetch source code
        PythonSourceCode sourceCode = functionSpec.getSource();

        //build empty nuclio definition
        String nuclioFunction = buildNuclioFunction(null, null);
        String handler = buildHandler(sourceCode);

        //requirements
        List<String> requirements = buildRequirements(baseImage, functionSpec.getRequirements());

        //read source and build context
        List<ContextRef> contextRefs = PythonRunnerHelper.createContextRefs(sourceCode);
        List<ContextSource> contextSources = new ArrayList<>(
            PythonRunnerHelper.createContextSources(entrypoint, handler, nuclioFunction, sourceCode, requirements)
        );

        //inject custom passwd to add our user
        if (passwdFile != null) {
            ContextSource entry = ContextSource
                .builder()
                .name("passwd-template")
                .base64(Base64.getEncoder().encodeToString(passwdFile.getBytes(StandardCharsets.UTF_8)))
                .build();
            contextSources.add(entry);
        }

        // Generate string docker file
        String dockerfile = generateDockerfile(pythonVersion, baseImage, requirements, taskSpec.getInstructions());

        // Parse run spec
        RunSpecAccessor runSpecAccessor = RunSpecAccessor.with(run.getSpec());

        //build image name
        String imageName =
            K8sBuilderHelper.sanitizeNames(runSpecAccessor.getProject()) +
            "-" +
            K8sBuilderHelper.sanitizeNames(runSpecAccessor.getFunction());

        //evaluate user provided image name
        if (StringUtils.hasText(functionSpec.getImage())) {
            String name = functionSpec.getImage().split(":")[0]; //remove tag if present
            if (StringUtils.hasText(name) && name.length() > MIN_IMAGE_NAME_LENGTH) {
                imageName = name;
            }
        }

        return K8sContainerBuilderRunnable
            .builder()
            .id(run.getId())
            .project(run.getProject())
            .runtime(PythonRuntime.RUNTIME)
            .task(PythonBuildTaskSpec.KIND)
            .state(State.READY.name())
            .labels(
                k8sBuilderHelper != null
                    ? List.of(new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction()))
                    : null
            )
            //base
            .image(imageName)
            .contextRefs(contextRefs)
            .contextSources(contextSources)
            .envs(coreEnvList)
            .secrets(coreSecrets)
            .resources(k8sBuilderHelper != null ? k8sBuilderHelper.convertResources(taskSpec.getResources()) : null)
            .volumes(taskSpec.getVolumes())
            .template(taskSpec.getProfile())
            // Task Specific
            .dockerFile(dockerfile)
            //specific
            .build();
    }
}
