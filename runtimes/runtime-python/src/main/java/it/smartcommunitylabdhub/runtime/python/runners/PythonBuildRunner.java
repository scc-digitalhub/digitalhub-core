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

import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.kaniko.infrastructure.docker.DockerfileGenerator;
import it.smartcommunitylabdhub.framework.kaniko.infrastructure.docker.DockerfileGeneratorFactory;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sContainerBuilderRunnable;
import it.smartcommunitylabdhub.runtime.python.PythonRuntime;
import it.smartcommunitylabdhub.runtime.python.specs.PythonBuildRunSpec;
import it.smartcommunitylabdhub.runtime.python.specs.PythonBuildTaskSpec;
import it.smartcommunitylabdhub.runtime.python.specs.PythonFunctionSpec;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
public class PythonBuildRunner {

    private final Map<String, String> images;
    private final String command;

    private final K8sBuilderHelper k8sBuilderHelper;
    private final List<String> dependencies;
    private final Map<String, String> serverlessImages;

    public PythonBuildRunner(Map<String, String> images, Map<String, String> serverlessImages, String command, K8sBuilderHelper k8sBuilderHelper, List<String> dependencies) {
        this.images = images;
        this.serverlessImages = serverlessImages;
        this.command = command;
        this.k8sBuilderHelper = k8sBuilderHelper;
        this.dependencies = dependencies;
    }

    public K8sContainerBuilderRunnable produce(Run run, Map<String, String> secretData) {
        PythonBuildRunSpec runSpec = new PythonBuildRunSpec(run.getSpec());
        PythonBuildTaskSpec taskSpec = runSpec.getTaskBuildSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());
        PythonFunctionSpec functionSpec = runSpec.getFunctionSpec();

        // core envs
        List<CoreEnv> coreEnvList = PythonRunnerHelper.createEnvList(run, taskSpec);    
        // core secrets
        List<CoreEnv> coreSecrets = PythonRunnerHelper.createSecrets(secretData);

        HashMap<String, Serializable> triggers = new HashMap<>();
        HashMap<String, Serializable> trigger = new HashMap<>(Map.of("kind", "http", "maxWorkers", 2));
        triggers.put("http", trigger);

        //read source and build context
        List<ContextRef> contextRefs = PythonRunnerHelper.createContextRefs(functionSpec);
        List<ContextSource> contextSources = PythonRunnerHelper.createContextSources(
            functionSpec,
            null,
            triggers,
            "_serve_handler",
            dependencies
        );

        // Generate docker file
        DockerfileGeneratorFactory dockerfileGenerator = DockerfileGenerator.factory();

        String image = images.get(functionSpec.getPythonVersion().name());
        String baseImage = StringUtils.hasText(functionSpec.getBaseImage()) ? functionSpec.getBaseImage() : image;

        // Add base Image
        dockerfileGenerator.from(baseImage);

        String serverlessImage = serverlessImages.get(functionSpec.getPythonVersion().name());
        if (serverlessImage != null && StringUtils.hasText(serverlessImage)) {
            dockerfileGenerator.copy("--from=" + serverlessImage + " /opt/nuclio/", "/opt/nuclio/");
            // TODO uhttpc
            dockerfileGenerator.copy(
                "--from=" + serverlessImage + " /opt/nuclio/processor",
                "/usr/local/bin/"
            );
            // Copy /shared folder (as workdir)
            dockerfileGenerator.copy(".", "/shared");
            dockerfileGenerator.copy("--from=ghcr.io/astral-sh/uv:latest /uv /uvx", "/bin/");
            // install common requirements
            dockerfileGenerator.run(
                "uv venv"
            );
            dockerfileGenerator.run(
                "uv pip install --no-index --find-links /opt/nuclio/pywhl -r /opt/nuclio/requirements/common.txt"
            );

            //set workdir from now on
            dockerfileGenerator.workdir("/shared");

            // Add user instructions
            Optional
                .ofNullable(taskSpec.getInstructions())
                .ifPresent(instructions -> instructions.forEach(dockerfileGenerator::run));

            // If requirements.txt are defined add to build
            if (functionSpec.getRequirements() != null && !functionSpec.getRequirements().isEmpty()) {
                // install all requirements
                dockerfileGenerator.run("uv pip install -r /shared/requirements.txt");
            }

        } else {
            // Copy toolkit from builder if required
            if (!image.equals(baseImage)) {
                dockerfileGenerator.copy("--from=" + image + " /opt/nuclio/", "/opt/nuclio/");
                dockerfileGenerator.copy(
                    "--from=" + image + " /usr/local/bin/processor  /usr/local/bin/uhttpc",
                    "/usr/local/bin/"
                );
            }

            // Copy /shared folder (as workdir)
            dockerfileGenerator.copy(".", "/shared");

            // install all requirements
            dockerfileGenerator.run(
                "python /opt/nuclio/whl/$(basename /opt/nuclio/whl/pip-*.whl)/pip install pip --no-index --find-links /opt/nuclio/whl " +
                "&& python -m pip install -r /opt/nuclio/requirements/common.txt"
            );

            //set workdir from now on
            dockerfileGenerator.workdir("/shared");

            // Add user instructions
            Optional
                .ofNullable(taskSpec.getInstructions())
                .ifPresent(instructions -> instructions.forEach(dockerfileGenerator::run));

            // If requirements.txt are defined add to build
            if (functionSpec.getRequirements() != null && !functionSpec.getRequirements().isEmpty()) {
                // install all requirements
                dockerfileGenerator.run("python -m pip install -r /shared/requirements.txt");
            }
        }

        // Set entry point
        dockerfileGenerator.entrypoint(List.of(command));

        // Generate string docker file
        String dockerfile = dockerfileGenerator.build().generate();

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
            if (StringUtils.hasText(name) && name.length() > 3) {
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
