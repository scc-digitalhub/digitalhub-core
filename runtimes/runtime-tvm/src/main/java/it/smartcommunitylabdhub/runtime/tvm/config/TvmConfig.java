/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.config;

import it.smartcommunitylabdhub.commons.config.YamlPropertySourceFactory;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmCompileSchemaModule;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/runtime-tvm.yaml", factory = YamlPropertySourceFactory.class)
public class TvmConfig {

    @Bean
    public TvmCompileSchemaModule tvmCompileSchemaModule(TvmProperties properties) {
        return new TvmCompileSchemaModule(properties);
    }

    @Bean("tvmProperties")
    @Primary
    @ConfigurationProperties(prefix = "runtime.tvm", ignoreUnknownFields = false)
    public TvmProperties tvmProperties() {
        return new TvmProperties();
    }
}
