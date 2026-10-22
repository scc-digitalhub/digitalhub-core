/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.config;

import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmCompileSchemaModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/runtime-tvm.yaml", factory = TvmYamlPropertySourceFactory.class)
@Import(TvmConfig.TargetOverrides.class)
public class TvmConfig {

    @ConditionalOnProperty("runtime.tvm.config")
    @PropertySource(value = "${runtime.tvm.config}", factory = TvmYamlPropertySourceFactory.class)
    static class TargetOverrides {}

    @Bean
    public TvmCompileSchemaModule tvmCompileSchemaModule(TvmProperties properties) {
        return new TvmCompileSchemaModule(properties);
    }

    @Bean("tvmProperties")
    @Primary
    @ConfigurationProperties(prefix = "runtime.tvm", ignoreUnknownFields = true)
    public TvmProperties tvmProperties() {
        return new TvmProperties();
    }
}
