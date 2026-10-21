/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.config;

import it.smartcommunitylabdhub.commons.config.YamlPropertySourceFactory;
import java.io.IOException;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;

public class TvmYamlPropertySourceFactory extends YamlPropertySourceFactory {
    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource resource) throws IOException {
        MapPropertySource source = (MapPropertySource) super.createPropertySource(name, resource);
        // Bundled and external files may both be named runtime-tvm.yaml.
        return new OriginTrackedMapPropertySource(resource.getResource().getDescription(), source.getSource());
    }
}
