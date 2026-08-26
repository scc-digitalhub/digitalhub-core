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
import it.smartcommunitylabdhub.console.config.ConsoleProperties;
import it.smartcommunitylabdhub.console.controllers.ConsoleController;
import it.smartcommunitylabdhub.logs.LogService;
import it.smartcommunitylabdhub.metrics.ResourceMetricsService;
import it.smartcommunitylabdhub.search.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
public class ConsoleConfigProvider implements ConfigurationProvider, InitializingBean {

    private ConsoleConfig config;

    public ConsoleConfigProvider(
        ConsoleProperties consoleProperties,
        ApplicationProperties properties,
        SecurityProperties securityProperties
    ) {
        Assert.notNull(consoleProperties, "console properties can not be null");
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

        builder
            .runMetrics(consoleProperties.getRunMetrics())
            .userMetrics(consoleProperties.getUserMetrics())
            .instanceMetrics(consoleProperties.getInstanceMetrics())
            .projectMetrics(consoleProperties.getProjectMetrics());

        this.config = builder.build();

        //disable feature flags by default
        config.setEnableSearch(false);
        config.setEnableLogs(false);
        config.setEnableMetrics(false);
    }

    @Autowired(required = false)
    public void setLogService(LogService logService) {
        if (logService != null) {
            config.setEnableLogs(true);
        }
    }

    @Autowired(required = false)
    public void setSearchService(SearchService searchService) {
        if (searchService != null) {
            config.setEnableSearch(true);
        }
    }

    @Autowired(required = false)
    public void setMetricsService(ResourceMetricsService metricsService) {
        if (metricsService != null) {
            config.setEnableMetrics(true);
        }
    }

    @Override
    public ConsoleConfig getConfig() {
        return config;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("config: {}", config.toJson());
        }
    }
}
