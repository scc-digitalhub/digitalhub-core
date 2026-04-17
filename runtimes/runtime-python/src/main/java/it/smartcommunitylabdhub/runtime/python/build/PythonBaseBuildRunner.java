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

import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.kaniko.infrastructure.docker.DockerfileGenerator;
import it.smartcommunitylabdhub.framework.kaniko.infrastructure.docker.DockerfileInstruction;
import it.smartcommunitylabdhub.framework.kaniko.infrastructure.docker.DockerfileGeneratorFactory;
import it.smartcommunitylabdhub.runtime.python.config.PythonProperties;
import jakarta.annotation.Nullable;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
public abstract class PythonBaseBuildRunner extends PythonBaseRunner {

    protected PythonBaseBuildRunner(PythonProperties properties, K8sBuilderHelper k8sBuilderHelper) {
        super(properties, k8sBuilderHelper);
    }

    protected String generateDockerfile(
        String pythonVersion,
        @Nullable String baseImage,
        @Nullable List<String> requirements,
        @Nullable List<String> instructions,
        @Nullable List<CoreEnv> envs
    ) {
        // Generate docker file
        DockerfileGeneratorFactory dockerfileGenerator = DockerfileGenerator.factory();

        String image = pythonVersion != null ? images.get(pythonVersion) : null;
        String defaultBaseImage = pythonVersion != null ? baseImages.get(pythonVersion) : null;
        String layerImage = pythonVersion != null ? serverlessImages.get(pythonVersion) : null;

        String fromImage = StringUtils.hasText(baseImage)
            ? baseImage
            : StringUtils.hasText(image) ? image : defaultBaseImage;

        if (!StringUtils.hasText(fromImage)) {
            throw new CoreRuntimeException(
                "no valid base image found for python version " +
                String.valueOf(pythonVersion) +
                " and no base image explicitly set"
            );
        }

        // Add base Image
        dockerfileGenerator.from(fromImage);

        // env vars from task spec
        if (envs != null) {
            envs.forEach(env -> dockerfileGenerator.instruction(DockerfileInstruction.Kind.ARG, env.name() + "=" + env.value()));
            envs.forEach(env -> dockerfileGenerator.instruction(DockerfileInstruction.Kind.ENV, env.name() + "=$" + env.name()));
        }

        //switch to root to install libs and dependencies
        // we will switch to final user at the end of the docker file
        dockerfileGenerator.user("root");

        //copy passwd file with user if template was correctly built
        if (this.passwdFile != null) {
            dockerfileGenerator.copy("passwd-template", "/etc/passwd");
        }

        // build from layer if user explicitly set base image or no predefined image is set
        // otherwise we'll build from pre-built
        boolean useLayer = !fromImage.equals(image);
        if (useLayer) {
            // since we are using a custom base image we need to copy
            // nuclio and uhttpc binaries and common requirements to the image

            // nuclio
            dockerfileGenerator.copy("--from=" + layerImage + " /opt/nuclio/", "/opt/nuclio/");
            // uhttpc
            dockerfileGenerator.copy(
                "--from=nuclio/uhttpc:0.0.1-amd64 /home/nuclio/bin/uhttpc",
                "/usr/local/bin/uhttpc"
            );

            // install common requirements
            dockerfileGenerator.run(
                "/opt/nuclio/uv/uv pip install --system --no-index --find-links /opt/nuclio/pywhl " +
                "-r /opt/nuclio/requirements/nuclio.txt"
            );
            dockerfileGenerator.run(
                "/opt/nuclio/uv/uv pip install --system --no-index --find-links /opt/nuclio/pywhl " +
                "-r /opt/nuclio/requirements/common.txt"
            );
        }

        // Copy /shared folder (as workdir)
        dockerfileGenerator.copy(".", homeDir);

        //set workdir from now on
        dockerfileGenerator.workdir(homeDir);

        // Add user instructions
        // NOTE: we let user run as ROOT as they might need to install packages and dependencies
        // we will switch to final user at the end of the docker file
        if (instructions != null) {
            instructions.forEach(dockerfileGenerator::run);
        }

        // If requirements.txt are defined add to build
        // we do it after user instructions as they might install prerequisites
        if (requirements != null && !requirements.isEmpty()) {
            // install all requirements
            dockerfileGenerator.run("/opt/nuclio/uv/uv pip install --system -r " + homeDir + "/requirements.txt");
        }

        //switch to final user
        dockerfileGenerator.user(String.valueOf(this.userId));

        //set workdir from now on
        dockerfileGenerator.workdir(homeDir);

        // Set entry point
        dockerfileGenerator.entrypoint(List.of(command));

        return dockerfileGenerator.build().generate();
    }
}
