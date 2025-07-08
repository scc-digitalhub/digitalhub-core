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

package it.smartcommunitylabdhub.core.components.config;

import com.fasterxml.jackson.databind.JsonNode;
import it.smartcommunitylabdhub.commons.infrastructure.Configuration;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.utils.SchemaUtils;
import it.smartcommunitylabdhub.core.CoreApplication;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Profile("generate-envs")
public class EnvExportRunner implements CommandLineRunner {

    @Autowired
    ApplicationContext context;

    @Override
    public void run(String... args) throws Exception {
        log.info("Running envs exporter...");

        String path = "ENV_VARS.txt";
        Set<String> envs = new HashSet<>();

        int returnCode = 0;
        try {
            //scan classpath for envs
            ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(
                false
            );

            scanner.addIncludeFilter(new AssignableTypeFilter(Configuration.class));
            scanner.addIncludeFilter(new AssignableTypeFilter(Credentials.class));

            List<String> basePackages = getBasePackages();
            log.info("Scanning for configuration under packages {}", basePackages);

            for (String basePackage : basePackages) {
                Set<BeanDefinition> list = scanner.findCandidateComponents(basePackage);
                list.forEach(beanDef -> {
                    try {
                        Class<?> clazz = Class.forName(beanDef.getBeanClassName());
                        if (Configuration.class.isAssignableFrom(clazz) || Credentials.class.isAssignableFrom(clazz)) {
                            log.debug("found " + clazz.getName());

                            //generate schema to collect all properties regardless of annotations
                            JsonNode schema = SchemaUtils.generator().generateSchema(clazz);
                            List<String> cenvs = new ArrayList<>();

                            if (schema != null && schema.get("properties") != null) {
                                schema.get("properties").fieldNames().forEachRemaining(cenvs::add);
                            }

                            log.trace("enumerated envs for {}: {}", clazz.getName(), cenvs);

                            envs.addAll(cenvs);
                        }
                    } catch (ClassNotFoundException | IllegalArgumentException | SecurityException e) {
                        log.error("Error loading class {}: {}", beanDef.getBeanClassName(), e.getMessage());
                    }
                });
            }

            //sort envs
            log.info("Found {} envs", envs.size());
            List<String> envsList = new ArrayList<>(envs);
            Collections.sort(envsList);

            //write to file
            log.info("Writing envs to {}", path);
            StringBuilder sb = new StringBuilder();
            for (String env : envsList) {
                sb.append(env.toUpperCase()).append("\n");
            }

            Files.write(Paths.get(path), sb.toString().getBytes(Charset.defaultCharset()));
            log.info("Envs exported successfully to {}", path);
        } catch (IOException e) {
            log.error("Error with exporter: {}", e.getMessage());
            returnCode = 1;
        }

        int exitCode = returnCode == 0
            ? SpringApplication.exit(context, () -> 0)
            : SpringApplication.exit(context, () -> 1);
        System.exit(exitCode);
    }

    private List<String> getBasePackages() {
        List<String> basePackages = new ArrayList<>();
        ComponentScan componentScan = CoreApplication.class.getAnnotation(ComponentScan.class);
        if (componentScan != null) {
            Collections.addAll(basePackages, componentScan.basePackages());
        }
        if (basePackages.isEmpty()) {
            throw new IllegalArgumentException("Base package not specified in @ComponentScan");
        }
        return basePackages;
    }
}
