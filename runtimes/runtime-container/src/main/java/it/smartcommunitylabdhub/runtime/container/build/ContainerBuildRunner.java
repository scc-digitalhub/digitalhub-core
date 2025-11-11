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

package it.smartcommunitylabdhub.runtime.container.build;

import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.objects.SourceCode;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.kaniko.infrastructure.docker.DockerfileGenerator;
import it.smartcommunitylabdhub.framework.kaniko.infrastructure.docker.DockerfileGeneratorFactory;
import it.smartcommunitylabdhub.framework.kaniko.infrastructure.docker.DockerfileInstruction;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sContainerBuilderRunnable;
import it.smartcommunitylabdhub.runtime.container.ContainerRuntime;
import it.smartcommunitylabdhub.runtime.container.specs.ContainerFunctionSpec;
import it.smartcommunitylabdhub.runtime.container.specs.ContainerFunctionSpec.SourceCodeLanguages;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
public class ContainerBuildRunner {

    private static final String RUN_DEBUG =
        "PWD=`pwd`;echo \"DEBUG: dir ${PWD}\";LS=`ls -R`;echo \"DEBUG: dir content:\" && echo \"${LS}\";";

    private static final int MIN_NAME_LENGTH = 3;

    private Set<String> ALLOWED_INSTRUCTIONS = Set.of(
        "FROM",
        "RUN",
        "CMD",
        "EXPOSE",
        "ENV",
        "ADD",
        "COPY",
        "ENTRYPOINT",
        "USER",
        "WORKDIR",
        "ARG",
        "HEALTHCHECK"
    );

    private final K8sBuilderHelper k8sBuilderHelper;

    public ContainerBuildRunner(K8sBuilderHelper k8sBuilderHelper) {
        this.k8sBuilderHelper = k8sBuilderHelper;
    }

    public void setAllowedInstructions(Set<String> allowedInstructions) {
        if (allowedInstructions != null) {
            ALLOWED_INSTRUCTIONS =
                Collections.unmodifiableSet(
                    allowedInstructions.stream().map(String::toUpperCase).collect(Collectors.toSet())
                );
        } else {
            ALLOWED_INSTRUCTIONS = Collections.emptySet();
        }
    }

    public K8sContainerBuilderRunnable produce(Run run, Map<String, String> secretData) {
        ContainerBuildRunSpec runSpec = new ContainerBuildRunSpec(run.getSpec());
        ContainerBuildTaskSpec taskSpec = runSpec.getTaskBuildSpec();
        ContainerFunctionSpec functionSpec = runSpec.getFunctionSpec();

        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());

        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
        );

        List<CoreEnv> coreSecrets = secretData == null
            ? null
            : secretData.entrySet().stream().map(e -> new CoreEnv(e.getKey(), e.getValue())).toList();

        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

        // Generate docker file
        DockerfileGeneratorFactory dockerfileGenerator = DockerfileGenerator.factory();

        String sourceDockerfile = null;
        if (functionSpec.getSource() != null && StringUtils.hasText(functionSpec.getSource().getBase64())) {
            SourceCode<SourceCodeLanguages> source = functionSpec.getSource();
            if (SourceCodeLanguages.dockerfile == source.getLang()) {
                //read source as dockerfile
                sourceDockerfile = new String(java.util.Base64.getDecoder().decode(source.getBase64()));
            }
        }

        List<String> instructions = taskSpec.getInstructions() != null
            ? new ArrayList<>(taskSpec.getInstructions())
            : new ArrayList<>();

        if (sourceDockerfile == null) {
            String baseImage = functionSpec.getBaseImage();
            if (StringUtils.hasText(baseImage)) {
                // Add image to docker file
                dockerfileGenerator.from(baseImage);
            }

            // copy context content to workdir
            dockerfileGenerator.copy(".", "/shared");
            dockerfileGenerator.workdir("/shared");

            if (log.isDebugEnabled()) {
                //add debug instructions to docker file
                dockerfileGenerator.run(RUN_DEBUG);
            }
        } else {
            //read source dockerfile
            String[] lines = sourceDockerfile.split("\\r?\\n");
            for (String line : lines) {
                if (StringUtils.hasText(line) && !line.trim().startsWith("#")) {
                    dockerfileGenerator.read(line);
                }
            }
        }

        // Add Instructions to docker file
        instructions.forEach(i -> {
            //keep only whitelisted
            DockerfileInstruction instruction = DockerfileInstruction.read(i);
            if (ALLOWED_INSTRUCTIONS.contains(instruction.getInstruction().name())) {
                dockerfileGenerator.instruction(
                    instruction.getInstruction(),
                    instruction.getArgs(),
                    instruction.getOpts()
                );
            }
        });

        // Seal generator
        DockerfileGenerator generator = dockerfileGenerator.build();

        //check first instruction is FROM
        if (
            generator.getInstructions().isEmpty() ||
            DockerfileInstruction.Kind.FROM != generator.getInstructions().getFirst().getInstruction()
        ) {
            throw new IllegalArgumentException("instructions must start with FROM");
        }

        // Generate string docker file
        String dockerfile = generator.generate();

        //read source and build context
        List<ContextRef> contextRefs = null;
        List<ContextSource> contextSources = null;

        if (functionSpec.getSource() != null && StringUtils.hasText(functionSpec.getSource().getSource())) {
            SourceCode<SourceCodeLanguages> source = functionSpec.getSource();

            try {
                //evaluate if local path (no scheme)
                UriComponents uri = UriComponentsBuilder.fromUriString(source.getSource()).build();
                String scheme = uri.getScheme();

                if (scheme != null) {
                    //write as ref
                    contextRefs = Collections.singletonList(ContextRef.from(source.getSource()));
                } else {
                    //write as source
                    String path = source.getSource();
                    if (StringUtils.hasText(source.getBase64())) {
                        contextSources =
                            Collections.singletonList(
                                (ContextSource.builder().name(path).base64(source.getBase64()).build())
                            );
                    }
                }
            } catch (IllegalArgumentException e) {
                //skip invalid source
            }
        }

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
            if (StringUtils.hasText(name) && name.length() > MIN_NAME_LENGTH) {
                imageName = name;
            }
        }

        // Build runnable
        return K8sContainerBuilderRunnable
            .builder()
            .id(run.getId())
            .project(run.getProject())
            .runtime(ContainerRuntime.RUNTIME)
            .task(ContainerBuildTaskSpec.KIND)
            .state(State.READY.name())
            .labels(
                k8sBuilderHelper != null
                    ? List.of(new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction()))
                    : null
            )
            // Base
            .image(imageName)
            .imagePullPolicy(functionSpec.getImagePullPolicy())
            .envs(coreEnvList)
            .secrets(coreSecrets)
            .resources(k8sBuilderHelper != null ? k8sBuilderHelper.convertResources(taskSpec.getResources()) : null)
            .volumes(taskSpec.getVolumes())
            .nodeSelector(taskSpec.getNodeSelector())
            .affinity(taskSpec.getAffinity())
            .tolerations(taskSpec.getTolerations())
            .runtimeClass(taskSpec.getRuntimeClass())
            .priorityClass(taskSpec.getPriorityClass())
            // Task specific
            .dockerFile(dockerfile)
            .contextRefs(contextRefs)
            .contextSources(contextSources)
            // specific
            .build();
    }
}
