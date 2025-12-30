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

import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.schemas.Schema;
import it.smartcommunitylabdhub.commons.services.SchemaService;
import it.smartcommunitylabdhub.commons.utils.ClassPathUtils;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Profile("generate-schemas")
public class SchemaExportRunner implements CommandLineRunner, ApplicationContextAware {

    @Autowired
    ApplicationContext context;

    @Autowired
    SchemaService service;

    private List<String> types = Collections.emptyList();
    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void scanForEntities() {
        List<String> basePackages = ClassPathUtils.getBasePackages(applicationContext);
        log.info("Scanning for specDTOs under packages {}", basePackages);
        Set<Class<? extends BaseDTO>> classes = EntityUtils
            .scanForEntities(basePackages)
            .stream()
            .collect(Collectors.toSet());

        //persist unmodifiable
        this.types =
            Collections.unmodifiableList(
                classes.stream().map(s -> EntityUtils.getEntityName(s).toLowerCase()).toList()
            );
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Running spec exported...");

        String path = "specs/";
        int returnCode = 0;
        try {
            for (String type : types) {
                String dest = path + type;
                log.info("exporting specs for {} to {}...", type, dest);

                for (Schema schema : service.listSchemas(type)) {
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
