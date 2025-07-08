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

import java.util.LinkedList;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DockerfileGenerator {

    private final LinkedList<DockerfileInstruction> instructions;
    private final LinkedList<DockerfileDirective> directives;

    protected DockerfileGenerator() {
        instructions = new LinkedList<>();
        directives = new LinkedList<>();
    }

    /**
     * Appends an instruction of the given type.
     *
     * @param type        The type of instruction.
     * @param instruction The instruction to append.
     */
    public void addInstruction(DockerfileInstruction instruction) {
        instructions.add(instruction);
    }

    public void addInstruction(DockerfileInstruction.Kind instruction, String... args) {
        instructions.add(DockerfileInstruction.builder().instruction(instruction).args(args).build());
    }

    /**
     * Appends a Dockerfile directive.
     *
     * @param directive The Dockerfile directive to append.
     */
    public void addDirective(DockerfileDirective directive) {
        directives.add(directive);
    }

    public void addDirective(String directive, String value) {
        directives.add(DockerfileDirective.builder().directive(directive).value(value).build());
    }

    /**
     * Generates the Dockerfile content based on the configured properties.
     *
     * @return The generated Dockerfile content as a string.
     */
    public String generate() {
        StringBuilder content = new StringBuilder();

        //validate directives
        if (
            directives != null &&
            !directives.isEmpty() &&
            directives.stream().map(d -> d.getDirective()).collect(Collectors.toSet()).size() < directives.size()
        ) {
            //duplicated directives are illegal
            throw new IllegalArgumentException("duplicated directives found.");
        }

        //not empty instructions
        if (instructions == null || instructions.isEmpty()) {
            throw new IllegalArgumentException("empty or missing instructions.");
        }

        //must start with FROM or ARG
        if (
            DockerfileInstruction.Kind.FROM != instructions.getFirst().getInstruction() &&
            DockerfileInstruction.Kind.ARG != instructions.getFirst().getInstruction()
        ) {
            throw new IllegalArgumentException("instructions must start with FROM (or ARG)");
        }

        directives.forEach(dir -> {
            content.append(dir.write()).append("\n");
        });

        if (!directives.isEmpty()) {
            directives.forEach(dir -> {
                content.append(dir.write()).append("\n");
            });

            content.append("\n");
        }

        instructions.forEach(i -> {
            //TODO validate instruction
            content.append(i.write()).append("\n");
        });

        content.append("\n");

        return content.toString();
    }

    public static DockerfileGeneratorFactory factory() {
        return DockerfileGeneratorFactory.newInstance();
    }
}
