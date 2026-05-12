/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.ray.config;

import it.smartcommunitylabdhub.commons.config.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/runtime-ray.yml", factory = YamlPropertySourceFactory.class)
public class RayConfig {

    @Bean("rayProperties")
    @Primary
    @ConfigurationProperties(prefix = "runtime.ray", ignoreUnknownFields = true)
    public RayProperties rayProperties() {
        return new RayProperties();
    }
}
