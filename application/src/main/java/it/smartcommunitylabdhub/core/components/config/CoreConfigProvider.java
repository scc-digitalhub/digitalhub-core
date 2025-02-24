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

package it.smartcommunitylabdhub.core.components.config;

import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.infrastructure.ConfigurationProvider;
import it.smartcommunitylabdhub.core.components.config.CoreConfig.CoreConfigBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
@Slf4j
public class CoreConfigProvider implements ConfigurationProvider {

    private CoreConfig config;

    public CoreConfigProvider(ApplicationProperties properties) {
        Assert.notNull(properties, "properties can not be null");

        log.debug("Build configuration for provider...");

        //build config
        CoreConfigBuilder builder = CoreConfig
            .builder()
            .endpoint(properties.getEndpoint())
            .name(properties.getName())
            .version(properties.getVersion())
            .level(properties.getLevel())
            .api(properties.getApi());

        this.config = builder.build();

        if (log.isTraceEnabled()) {
            log.trace("config: {}", config.toJson());
        }
    }

    @Override
    public CoreConfig getConfig() {
        return config;
    }
}
