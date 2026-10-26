/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.config;

import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import org.springframework.beans.factory.config.YamlProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;

public class TvmYamlPropertySourceFactory extends YamlProcessor implements PropertySourceFactory {
    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource resource) throws IOException {
        setResources(resource.getResource());
        var values = new LinkedHashMap<String, Object>();
        process((properties, yaml) -> {
            values.putAll(getFlattenedMap(yaml));
            var profiles = JacksonMapper.OBJECT_MAPPER.valueToTree(yaml).at("/runtime/tvm/targets");
            for (var profile : profiles.properties()) {
                String prefix = "runtime.tvm.targets." + profile.getKey();
                if (profile.getValue().has("cross-cc") || profile.getValue().has("cross-cc-options")) {
                    throw new IllegalArgumentException(prefix + ": use linker.command and linker.options");
                }
                var target = profile.getValue().path("target");
                if (target.isMissingNode()) continue;
                if (!target.isObject() || target.isEmpty()) {
                    throw new IllegalArgumentException(prefix + ".target must be a non-empty YAML mapping");
                }
                String key = prefix + ".target";
                // Keep lists and numbers intact instead of binding indexed properties to a map.
                values.keySet().removeIf(property -> property.startsWith(key + ".") || property.startsWith(key + "["));
                values.put(key, target.toString());
            }
        });
        // Bundled and external files may both be named runtime-tvm.yaml.
        return new MapPropertySource(resource.getResource().getDescription(), values);
    }
}
