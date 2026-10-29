/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.config;

import it.smartcommunitylabdhub.commons.config.YamlPropertySourceFactory;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmCompileSchemaModule;
import java.io.IOException;
import java.util.LinkedHashMap;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.util.StringUtils;

@Configuration
@PropertySource(value = "classpath:/runtime-tvm.yaml", factory = YamlPropertySourceFactory.class)
public class TvmConfig {

    @Bean
    public TvmCompileSchemaModule tvmCompileSchemaModule(TvmProperties properties) {
        return new TvmCompileSchemaModule(properties);
    }

    @Bean("tvmProperties")
    @Primary
    public TvmProperties tvmProperties(ConfigurableEnvironment environment, ResourceLoader resources) throws IOException {
        String config = environment.getProperty("runtime.tvm.config");
        if (StringUtils.hasText(config)) {
            var source = (MapPropertySource) new YamlPropertySourceFactory().createPropertySource(
                null, new EncodedResource(resources.getResource(config))
            );
            environment.getPropertySources().addBefore(
                "runtime-tvm.yaml", new OriginTrackedMapPropertySource("tvm-overrides", source.getSource())
            );
        }
        var binder = Binder.get(environment);
        var properties = binder.bindOrCreate("runtime.tvm", TvmProperties.class);
        properties.setTargets(binder.bind(
            "runtime.tvm.targets", Bindable.mapOf(String.class, TvmProperties.Target.class),
            new NoUnboundElementsBindHandler(BindHandler.DEFAULT)
        ).orElseGet(LinkedHashMap::new));
        return properties;
    }
}
