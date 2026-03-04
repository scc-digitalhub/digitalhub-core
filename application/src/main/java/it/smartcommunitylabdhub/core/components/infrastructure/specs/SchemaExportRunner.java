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

import it.smartcommunitylabdhub.commons.models.schemas.Schema;
import it.smartcommunitylabdhub.commons.services.SchemaService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Profile("generate-schemas")
public class SchemaExportRunner implements CommandLineRunner {

    @Autowired
    ApplicationContext context;

    @Autowired
    List<SchemaService<?>> services;

    @Override
    public void run(String... args) throws Exception {
        log.info("Running spec exported...");

        String path = "specs/";
        int returnCode = 0;
        try {
            for (SchemaService<?> service : services) {
                log.info("exporting specs for {}...", service);

                for (Schema schema : service.listSchemas()) {
                    if (schema.entity() == null) {
                        log.warn("skipping schema with null entity: {}", schema);
                        continue;
                    }
                    String type = schema.entity().toLowerCase();
                    String dest = path + type;
                    String out = dest + "/" + schema.kind() + ".json";
                    Path fp = Paths.get(out);
                    Files.createDirectories(fp.getParent());

                    log.info("writing spec {}:{} to {}...", type, schema.kind(), out);

                    String jsonSchema = schema.schema().toPrettyString();
                    Files.write(fp, jsonSchema.getBytes(StandardCharsets.UTF_8));
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
