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

package it.smartcommunitylabdhub.framework.kaniko.infrastructure.docker;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Factory for creating instances of DockerfileGenerator to programmatically generate Dockerfiles.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create a DockerfileGenerator instance
 * DockerfileGenerator dockerfileGenerator = DockerfileGeneratorFactory.newInstance()
 *     .from("openjdk:11")
 *     .run("apt-get update && apt-get install -y git")
 *     .cmd("java -jar app.jar")
 *     .label("maintainer=\"John Doe <john@example.com>\"")
 *     .copy("target/*.jar", "/app/app.jar")
 *     .expose("8080")
 *     .user("myuser")
 *     .workdir("/app")
 *     .healthcheck("--interval=30s --timeout=30s CMD curl -f http://localhost/ || exit 1")
 *     .addDirective("ARG VERSION=latest\n")
 *     .build();
 * <p>
 * // Generate Dockerfile content
 * String dockerfileContent = dockerfileGenerator.generateDockerfile();
 * System.out.println("Generated Dockerfile:\n" + dockerfileContent);
 * <p>
 * // Write Dockerfile to file
 * dockerfileGenerator.writeToFile("Dockerfile");
 * System.out.println("Dockerfile written to file.");
 * }</pre>
 */
public class DockerfileGeneratorFactory {

    private final DockerfileGenerator generator;

    private DockerfileGeneratorFactory() {
        generator = new DockerfileGenerator();
    }

    public static DockerfileGeneratorFactory newInstance() {
        return new DockerfileGeneratorFactory();
    }

    public DockerfileGeneratorFactory from(String baseImage) {
        generator.addInstruction(DockerfileInstruction.Kind.FROM, baseImage);
        return this;
    }

    public DockerfileGeneratorFactory run(String run) {
        generator.addInstruction(DockerfileInstruction.Kind.RUN, run);
        return this;
    }

    public DockerfileGeneratorFactory entrypoint(List<String> entrypoint) {
        generator.addInstruction(
            DockerfileInstruction.Kind.ENTRYPOINT,
            "[" + entrypoint.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", ")) + "]"
        );
        return this;
    }

    public DockerfileGeneratorFactory cmd(List<String> command) {
        generator.addInstruction(
            DockerfileInstruction.Kind.CMD,
            "[" + command.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", ")) + "]"
        );
        return this;
    }

    public DockerfileGeneratorFactory label(String label) {
        generator.addInstruction(DockerfileInstruction.Kind.LABEL, label);
        return this;
    }

    public DockerfileGeneratorFactory copy(String source, String destination) {
        generator.addInstruction(DockerfileInstruction.Kind.COPY, source, " ", destination);
        return this;
    }

    public DockerfileGeneratorFactory expose(String port) {
        generator.addInstruction(DockerfileInstruction.Kind.EXPOSE, port);
        return this;
    }

    public DockerfileGeneratorFactory addDirective(String directive, String value) {
        generator.addDirective(directive, value);
        return this;
    }

    public DockerfileGeneratorFactory user(String user) {
        generator.addInstruction(DockerfileInstruction.Kind.USER, user);
        return this;
    }

    public DockerfileGeneratorFactory workdir(String workdir) {
        generator.addInstruction(DockerfileInstruction.Kind.WORKDIR, workdir);
        return this;
    }

    public DockerfileGeneratorFactory healthcheck(String healthCheck) {
        generator.addInstruction(DockerfileInstruction.Kind.HEALTHCHECK, healthCheck);
        return this;
    }

    public DockerfileGenerator build() {
        return generator;
    }
}
