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

package it.smartcommunitylabdhub.runtime.mlflow;

import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.accessors.fields.KeyAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.kaniko.infrastructure.docker.DockerfileGenerator;
import it.smartcommunitylabdhub.framework.kaniko.infrastructure.docker.DockerfileGeneratorFactory;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sContainerBuilderRunnable;
import it.smartcommunitylabdhub.models.Model;
import it.smartcommunitylabdhub.models.ModelManager;
import it.smartcommunitylabdhub.runtime.mlflow.specs.MlflowBuildRunSpec;
import it.smartcommunitylabdhub.runtime.mlflow.specs.MlflowBuildTaskSpec;
import it.smartcommunitylabdhub.runtime.mlflow.specs.MlflowServeFunctionSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
public class MlflowBuildRunner {

    private final String image;
    private final String command;

    private final K8sBuilderHelper k8sBuilderHelper;
    private final ModelManager modelService;

    public MlflowBuildRunner(
        String image,
        String command,
        ModelManager modelService,
        K8sBuilderHelper k8sBuilderHelper
    ) {
        this.image = image;
        this.command = command;
        this.modelService = modelService;
        this.k8sBuilderHelper = k8sBuilderHelper;
    }

    public K8sContainerBuilderRunnable produce(Run run, Map<String, String> secretData) {
        MlflowBuildRunSpec runSpec = MlflowBuildRunSpec.with(run.getSpec());
        MlflowBuildTaskSpec taskSpec = runSpec.getTaskBuildSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());
        MlflowServeFunctionSpec functionSpec = runSpec.getFunctionSpec();

        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
        );

        List<CoreEnv> coreSecrets = secretData == null
            ? null
            : secretData.entrySet().stream().map(e -> new CoreEnv(e.getKey(), e.getValue())).toList();

        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

        // Generate docker file
        DockerfileGeneratorFactory dockerfileGenerator = DockerfileGenerator.factory();

        String baseImage = image;

        // Add base Image
        dockerfileGenerator.from(baseImage);

        // Copy /shared folder (as workdir)
        dockerfileGenerator.copy(".", "/shared");

        // Parse run spec
        RunSpecAccessor runSpecAccessor = RunSpecAccessor.with(run.getSpec());

        //path is in run spec after build
        String path = runSpec.getPath();
        if (!StringUtils.hasText(path)) {
            throw new IllegalArgumentException("model path is missing or invalid");
        }

        if (path.startsWith(Keys.STORE_PREFIX)) {
            KeyAccessor keyAccessor = KeyAccessor.with(path);
            if (!EntityUtils.getEntityName(Model.class).equalsIgnoreCase(keyAccessor.getType())) {
                throw new CoreRuntimeException("invalid entity kind reference, expected model");
            }
            Model model = keyAccessor.getId() != null
                ? modelService.findModel(keyAccessor.getId())
                : modelService.getLatestModel(keyAccessor.getProject(), keyAccessor.getName());
            if (model == null) {
                throw new CoreRuntimeException("invalid entity reference, MLFlow model not found");
            }
            if (!model.getKind().equals("mlflow")) {
                throw new CoreRuntimeException("invalid entity reference, expected MLFlow model");
            }

            path = (String) model.getSpec().get("path");
            if (!path.endsWith(".zip")) {
                if (!path.endsWith("/")) {
                    path += "/";
                }
            }
        }

        UriComponents uri = UriComponentsBuilder.fromUriString(path).build();

        //read source and build context
        List<ContextSource> contextSources = new ArrayList<>();
        List<ContextRef> contextRefs = Collections.singletonList(
            ContextRef.builder().source(path).protocol(uri.getScheme()).destination("model").build()
        );

        //set workdir from now on
        dockerfileGenerator.workdir("/shared");

        // Add user instructions
        Optional
            .ofNullable(taskSpec.getInstructions())
            .ifPresent(instructions -> instructions.forEach(dockerfileGenerator::run));

        // install all requirements
        dockerfileGenerator.run("python -m pip install -r /shared/model/requirements.txt");

        // Set entry point
        dockerfileGenerator.entrypoint(List.of(command));

        // Generate string docker file
        String dockerfile = dockerfileGenerator.build().generate();

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
            .runtime(MlflowServeRuntime.RUNTIME)
            .task(MlflowBuildTaskSpec.KIND)
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
