/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * Copyright 2024 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.core.components.infrastructure.specs;

import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.schemas.Schema;
import it.smartcommunitylabdhub.commons.services.SpecRegistry;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Profile("generate-schemas")
public class SpecExportRunner implements CommandLineRunner {

    @Autowired
    ApplicationContext context;

    @Autowired
    SpecRegistry registry;

    @Override
    public void run(String... args) throws Exception {
        log.info("Running spec exported...");

        String path = "specs/";
        int returnCode = 0;
        try {
            for (EntityName entity : EntityName.values()) {
                String dest = path + entity.getValue();
                log.info("exporting specs for {} to {}...", entity.getValue(), dest);

                for (Schema schema : registry.listSchemas(entity)) {
                    String out = dest + "/" + schema.kind() + ".json";
                    Path fp = Paths.get(out);
                    Files.createDirectories(fp.getParent());

                    log.info("writing spec {}:{} to {}...", entity.getValue(), schema.kind(), out);

                    File file = new File(out);
                    String jsonSchema = schema.schema().toPrettyString();
                    FileUtils.writeStringToFile(file, jsonSchema, StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            log.error("Error with exported: {}", e.getMessage());
            returnCode = 1;
        }

        int exitCode = returnCode == 0
            ? SpringApplication.exit(context, () -> 0)
            : SpringApplication.exit(context, () -> 1);
        System.exit(exitCode);
    }
}
