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

package it.smartcommunitylabdhub.console.config;

import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.config.SecurityProperties;
import it.smartcommunitylabdhub.commons.config.YamlPropertySourceFactory;
import it.smartcommunitylabdhub.console.ConsoleConfigProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/console.yml", factory = YamlPropertySourceFactory.class)
@EnableConfigurationProperties({ ConsoleProperties.class })
public class ConsoleConfig {

    @Bean
    ConsoleConfigProvider consoleConfigProvider(
        ConsoleProperties consoleProperties,
        ApplicationProperties properties,
        SecurityProperties securityProperties
    ) {
        return new ConsoleConfigProvider(consoleProperties, properties, securityProperties);
    }
}
