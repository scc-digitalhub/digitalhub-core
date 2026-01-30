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

package it.smartcommunitylabdhub.runtime.guardrail.runners;

import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.kaniko.infrastructure.docker.DockerfileGenerator;
import it.smartcommunitylabdhub.framework.kaniko.infrastructure.docker.DockerfileGeneratorFactory;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sContainerBuilderRunnable;
import it.smartcommunitylabdhub.runtime.guardrail.GuardrailRuntime;
import it.smartcommunitylabdhub.runtime.guardrail.specs.GuardrailBuildRunSpec;
import it.smartcommunitylabdhub.runtime.guardrail.specs.GuardrailBuildTaskSpec;
import it.smartcommunitylabdhub.runtime.python.specs.PythonFunctionSpec;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
public class GuardrailBuildRunner extends GuardrailBaseRunner {

    public GuardrailBuildRunner(
        Map<String, String> images, 
        Map<String, String> serverlessImages,
        Map<String, String> baseImages,
        String command, 
        K8sBuilderHelper k8sBuilderHelper,
        List<String> dependencies
    ) {
        super(images, serverlessImages, baseImages, command, k8sBuilderHelper, dependencies);
    }

    public K8sContainerBuilderRunnable produce(Run run, Map<String, String> secretData) {

        GuardrailBuildRunSpec runSpec = new GuardrailBuildRunSpec(run.getSpec());
        GuardrailBuildTaskSpec taskSpec = runSpec.getTaskBuildSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(runSpec.getTaskBuildSpec().toMap());
        PythonFunctionSpec functionSpec = runSpec.getFunctionSpec();

        //prepare context
        Context ctx = prepareContext(run, secretData, runSpec, runSpec.getTaskBuildSpec(), runSpec.getFunctionSpec());  

        // Generate docker file
        DockerfileGeneratorFactory dockerfileGenerator = DockerfileGenerator.factory();

        String image = images.get(functionSpec.getPythonVersion().name());
        String defaultBaseImage = baseImages.get(functionSpec.getPythonVersion().name());
        String layerImage = serverlessImages.get(functionSpec.getPythonVersion().name());
 
        String baseImage = StringUtils.hasText(functionSpec.getBaseImage()) 
                    ? functionSpec.getBaseImage() 
                    : StringUtils.hasText(image) 
                        ? image 
                        : defaultBaseImage;

        // Add base Image
        dockerfileGenerator.from(baseImage);

        // build from layer if user explicitly set base image or no predefined image is set
        boolean useLayer = StringUtils.hasText(functionSpec.getBaseImage()) || !StringUtils.hasText(image);
        if (useLayer) {
            dockerfileGenerator.copy("--from=" + layerImage + " /opt/nuclio/", "/opt/nuclio/");
            // TODO uhttpc
            dockerfileGenerator.copy(
                "--from=" + layerImage + " /opt/nuclio/processor",
                "/usr/local/bin/"
            );
            // Copy /shared folder (as workdir)
            dockerfileGenerator.copy(".", "/shared");
            dockerfileGenerator.copy("--from=ghcr.io/astral-sh/uv:latest /uv /uvx", "/bin/");
            // install common requirements
            dockerfileGenerator.run(
                "uv pip install --system --no-index --find-links /opt/nuclio/pywhl -r /opt/nuclio/requirements/common.txt"
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
                .ofNullable(runSpec.getTaskBuildSpec().getInstructions())
                .ifPresent(instructions -> instructions.forEach(dockerfileGenerator::run));

            // If requirements.txt are defined add to build
            if (runSpec.getFunctionSpec().getRequirements() != null && !runSpec.getFunctionSpec().getRequirements().isEmpty()) {           
                // install all requirements
                dockerfileGenerator.run("python -m pip install -r /shared/requirements.txt");
            }
        }

        // Set command

        // Set entry point
        dockerfileGenerator.entrypoint(List.of(command));

        // Generate string docker file
        String dockerfile = dockerfileGenerator.build().generate();

        //merge env with PYTHON path override
        ctx.coreEnvList().add(new CoreEnv("PYTHONPATH", "${PYTHONPATH}:/shared/"));

        // Parse run spec
        RunSpecAccessor runSpecAccessor = RunSpecAccessor.with(run.getSpec());

        //build image name
        String imageName =
            K8sBuilderHelper.sanitizeNames(runSpecAccessor.getProject()) +
            "-" +
            K8sBuilderHelper.sanitizeNames(runSpecAccessor.getFunction());

        //evaluate user provided image name
        if (StringUtils.hasText(runSpec.getFunctionSpec().getImage())) {
            String name = runSpec.getFunctionSpec().getImage().split(":")[0]; //remove tag if present
            if (StringUtils.hasText(name) && name.length() > 3) {
                imageName = name;
            }
        }

        return K8sContainerBuilderRunnable
            .builder()
            .id(run.getId())
            .project(run.getProject())
            .runtime(GuardrailRuntime.RUNTIME)
            .task(GuardrailBuildTaskSpec.KIND)
            .state(State.READY.name())
            .labels(
                k8sBuilderHelper != null
                    ? List.of(new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction()))
                    : null
            )
            //base
            .image(imageName)
            .contextRefs(ctx.contextRefs())
            .contextSources(ctx.contextSources())
            .envs(ctx.coreEnvList())
            .secrets(ctx.coreSecrets())
            .resources(k8sBuilderHelper != null ? k8sBuilderHelper.convertResources(runSpec.getTaskBuildSpec().getResources()) : null)
            .volumes(runSpec.getTaskBuildSpec().getVolumes())
            .template(runSpec.getTaskBuildSpec().getProfile())
            // Task Specific
            .dockerFile(dockerfile)
            //specific
            .build();
    }
}
