/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package it.smartcommunitylabdhub.artifacts.config;

import it.smartcommunitylabdhub.artifacts.Artifact;
import it.smartcommunitylabdhub.artifacts.lifecycle.ArtifactFsmFactoryBuilder;
import it.smartcommunitylabdhub.core.specs.SpecRegistryImpl;
import it.smartcommunitylabdhub.fsm.Fsm;
import it.smartcommunitylabdhub.search.indexers.EntityIndexer;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ArtifactConfig {

    @Bean
    Fsm.Factory<String, String, Artifact> artifactFsmFactory(ArtifactFsmFactoryBuilder builder) {
        return builder.build();
    }

    @Bean
    SpecRegistryImpl<Artifact> artifactSpecRegistry() {
        return new SpecRegistryImpl<>(Artifact.class);
    }

    // build indexer only if a provider is available
    // NOTE: we can not use ConditionalOnBean on the EntityIndexer.Factory because
    // the optional bean could be missing when this is processed
    @Bean
    EntityIndexer<Artifact> artifactEntityIndexer(Optional<EntityIndexer.Factory> entityIndexerFactory) {
        return entityIndexerFactory.map(factory -> factory.build(Artifact.class)).orElse(null);
    }
}
