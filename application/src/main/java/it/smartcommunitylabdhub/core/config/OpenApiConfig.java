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

package it.smartcommunitylabdhub.core.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@Order(21)
//@ComponentScan(basePackages = {"org.springdoc"})
public class OpenApiConfig {

    @Value("${application.name}")
    private String name;

    @Value("${application.description}")
    private String description;

    @Value("${application.version}")
    private String version;

    @Bean
    OpenAPI coreMicroserviceOpenAPI() {
        return new OpenAPI().info(new Info().title(name).description(description).version(version));
    }

    @Bean
    public GroupedOpenApi apiCore() {
        return GroupedOpenApi
            .builder()
            .group("core-v1")
            .displayName("Core Base API V1")
            .packagesToScan("it.smartcommunitylabdhub.core.controllers.v1.base")
            .build();
    }

    @Bean
    public GroupedOpenApi apiContextCore() {
        return GroupedOpenApi
            .builder()
            .group("context-v1")
            .displayName("Core Context API V1")
            .packagesToScan("it.smartcommunitylabdhub.core.controllers.v1.context")
            .build();
    }
}
