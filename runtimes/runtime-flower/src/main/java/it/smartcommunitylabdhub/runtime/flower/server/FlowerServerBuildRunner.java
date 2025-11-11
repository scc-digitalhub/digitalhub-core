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

package it.smartcommunitylabdhub.runtime.flower.server;

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
import it.smartcommunitylabdhub.runtime.flower.model.FABModel;
import it.smartcommunitylabdhub.runtime.flower.server.specs.FlowerServerBuildRunSpec;
import it.smartcommunitylabdhub.runtime.flower.server.specs.FlowerServerBuildTaskSpec;
import it.smartcommunitylabdhub.runtime.flower.server.specs.FlowerServerFunctionSpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
public class FlowerServerBuildRunner {

    private final String image;
    private final String command;

    private final K8sBuilderHelper k8sBuilderHelper;

    public FlowerServerBuildRunner(String image, String command, K8sBuilderHelper k8sBuilderHelper) {
        this.image = image;
        this.command = command;

        this.k8sBuilderHelper = k8sBuilderHelper;
    }

    public K8sContainerBuilderRunnable produce(Run run, Map<String, String> secretData) {
        FlowerServerBuildRunSpec runSpec = new FlowerServerBuildRunSpec(run.getSpec());
        FlowerServerBuildTaskSpec taskSpec = runSpec.getTaskBuildSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());
        FlowerServerFunctionSpec functionSpec = runSpec.getFunctionSpec();

        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
        );

        List<CoreEnv> coreSecrets = secretData == null
            ? null
            : secretData.entrySet().stream().map(e -> new CoreEnv(e.getKey(), e.getValue())).toList();

        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

        // Generate docker file
        DockerfileGeneratorFactory dockerfileGenerator = DockerfileGenerator.factory();

        String baseImage = StringUtils.hasText(functionSpec.getBaseImage()) ? functionSpec.getBaseImage() : image;

        // Add base Image
        dockerfileGenerator.from(baseImage);

        // Copy /shared folder (as workdir)
        dockerfileGenerator.copy(".", "/app");

        // Parse run spec
        RunSpecAccessor runSpecAccessor = RunSpecAccessor.with(run.getSpec());

        //read source and build context
        List<ContextRef> contextRefs = null;
        List<ContextSource> contextSources = new ArrayList<>();

        FABModel fabModel = new FABModel();
        fabModel.setName(runSpecAccessor.getFunction());
        fabModel.setVersion("1.0.0");
        fabModel.setDependencies(new LinkedList<>());
        fabModel.getDependencies().add("flwr[rest]");
        if (functionSpec.getRequirements() != null && !functionSpec.getRequirements().isEmpty()) {
            fabModel.getDependencies().addAll(functionSpec.getRequirements());
        }
        fabModel.setDefaultFederation("core-federation");
        String toml = fabModel.toTOML();
        // convert toml to base64
        String tomlBase64 = Base64.getEncoder().encodeToString(toml.getBytes(StandardCharsets.UTF_8));
        contextSources.add(ContextSource.builder().name("pyproject.toml").base64(tomlBase64).build());

        //set workdir from now on
        dockerfileGenerator.workdir("/app");
        dockerfileGenerator.run("python -m pip install -U --no-cache-dir .");

        // Add user instructions
        Optional
            .ofNullable(taskSpec.getInstructions())
            .ifPresent(instructions -> instructions.forEach(dockerfileGenerator::run));

        // Set entry point
        dockerfileGenerator.entrypoint(List.of(command));

        // Generate string docker file
        String dockerfile = dockerfileGenerator.build().generate();

        //merge env with PYTHON path override
        coreEnvList.add(new CoreEnv("PYTHONPATH", "${PYTHONPATH}:/app/"));

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
            .runtime(FlowerServerRuntime.RUNTIME)
            .task(FlowerServerBuildTaskSpec.KIND)
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
            .nodeSelector(taskSpec.getNodeSelector())
            .affinity(taskSpec.getAffinity())
            .tolerations(taskSpec.getTolerations())
            // Task Specific
            .dockerFile(dockerfile)
            //specific
            .build();
    }
}
