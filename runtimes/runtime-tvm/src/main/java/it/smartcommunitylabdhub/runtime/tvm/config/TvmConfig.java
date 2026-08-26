/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.config;

import it.smartcommunitylabdhub.commons.config.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/runtime-tvm.yml", factory = YamlPropertySourceFactory.class)
public class TvmConfig {

    @Bean("tvmProperties")
    @Primary
    @ConfigurationProperties(prefix = "runtime.tvm", ignoreUnknownFields = true)
    public TvmProperties tvmProperties() {
        return new TvmProperties();
    }
}
