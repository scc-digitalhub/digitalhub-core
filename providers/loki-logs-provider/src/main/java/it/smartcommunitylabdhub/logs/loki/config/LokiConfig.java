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

package it.smartcommunitylabdhub.logs.loki.config;

import it.smartcommunitylabdhub.commons.config.YamlPropertySourceFactory;
import it.smartcommunitylabdhub.logs.LogService;
import it.smartcommunitylabdhub.logs.loki.LokiLogService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.annotation.Order;

@Configuration
@Order(3)
@PropertySource(value = "classpath:/loki-provider.yaml", factory = YamlPropertySourceFactory.class)
@EnableConfigurationProperties({ LokiProperties.class })
public class LokiConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "providers.loki", name = "url", matchIfMissing = false)
    public LogService lokiLogService(LokiProperties lokiProperties) {
        return new LokiLogService(lokiProperties);
    }
}
