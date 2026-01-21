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

package it.smartcommunitylabdhub.commons.config;

import java.io.IOException;
import java.util.Collections;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

public class YamlPropertySourceFactory implements PropertySourceFactory {

    @Override
    public @NonNull PropertySource<?> createPropertySource(
        @Nullable String name,
        @NonNull EncodedResource encodedResource
    ) throws IOException {
        // Use Spring Boot's YamlPropertySourceLoader which properly handles
        // environment variable placeholder resolution in multi-jar deployments
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

        String fileName = encodedResource.getResource().getFilename() != null
            ? encodedResource.getResource().getFilename()
            : name;

        var propertySources = loader.load(fileName, encodedResource.getResource());

        if (propertySources.isEmpty()) {
            // Return empty property source as fallback
            return new OriginTrackedMapPropertySource(fileName, Collections.emptyMap());
        }

        // Return the first property source (typically there's only one for simple YAML files)
        return propertySources.get(0);
    }
}
