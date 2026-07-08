/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * Copyright 2025 the original author or authors
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

package it.smartcommunitylabdhub.console;

import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.config.SecurityProperties;
import it.smartcommunitylabdhub.commons.infrastructure.ConfigurationProvider;
import it.smartcommunitylabdhub.console.controllers.ConsoleController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
public class ConsoleConfigProvider implements ConfigurationProvider {

    private ConsoleConfig config;

    public ConsoleConfigProvider(ApplicationProperties properties, SecurityProperties securityProperties) {
        Assert.notNull(properties, "properties can not be null");
        Assert.notNull(securityProperties, "securityProperties can not be null");

        log.debug("Build configuration for provider...");
        String applicationUrl = StringUtils.hasText(properties.getEndpoint()) ? properties.getEndpoint() : "";
        //build config
        ConsoleConfig.ConsoleConfigBuilder builder = ConsoleConfig.builder()
            .contextPath(ConsoleController.CONSOLE_CONTEXT)
            .applicationUrl(applicationUrl)
            .apiUrl(applicationUrl + "/api/v1")
            .wsUrl(applicationUrl + "/ws");

        if (securityProperties.isRequired()) {
            builder.authUrl(applicationUrl + ConsoleController.AUTH_PATH);
        }

        this.config = builder.build();

        if (log.isTraceEnabled()) {
            log.trace("config: {}", config.toJson());
        }
    }

    @Autowired
    public void setClarityKey(@Value("${frontend.clarity.key}") String clarityKey) {
        if (this.config != null) {
            this.config.setClarityKey(clarityKey);
        }
    }

    @Override
    public ConsoleConfig getConfig() {
        return config;
    }
}
