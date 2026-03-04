/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

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

package it.smartcommunitylabdhub.extensions.config;

import it.smartcommunitylabdhub.commons.config.YamlPropertySourceFactory;
import it.smartcommunitylabdhub.core.repositories.BaseEntityRepositoryImpl;
import it.smartcommunitylabdhub.core.repositories.SearchableEntityRepository;
import it.smartcommunitylabdhub.core.services.BaseEntityServiceImpl;
import it.smartcommunitylabdhub.core.services.EntityService;
import it.smartcommunitylabdhub.core.specs.SpecRegistryImpl;
import it.smartcommunitylabdhub.extensions.model.Extension;
import it.smartcommunitylabdhub.extensions.model.ExtensionDefinition;
import it.smartcommunitylabdhub.extensions.persistence.ExtensionDefinitionEntity;
import it.smartcommunitylabdhub.extensions.persistence.ExtensionEntity;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.jpa.repository.JpaRepository;

@Configuration
@PropertySource(value = "classpath:/component-extensions.yml", factory = YamlPropertySourceFactory.class)
@EnableConfigurationProperties({ ExtensionsProperties.class })
public class ExtensionConfig {

    @Bean
    SearchableEntityRepository<ExtensionEntity, Extension> extensionSearchableEntityRepository(
        JpaRepository<ExtensionEntity, String> repository,
        Converter<Extension, ExtensionEntity> entityBuilder,
        Converter<ExtensionEntity, Extension> dtoBuilder
    ) {
        return new BaseEntityRepositoryImpl<>(repository, entityBuilder, dtoBuilder) {};
    }

    @Bean
    SearchableEntityRepository<
        ExtensionDefinitionEntity,
        ExtensionDefinition
    > extensionDefinitionSearchableEntityRepository(JpaRepository<ExtensionDefinitionEntity, String> repository) {
        return new BaseEntityRepositoryImpl<>(repository) {};
    }

    @Bean
    SpecRegistryImpl<ExtensionDefinition> extensionDefinitionSpecRegistry() {
        return new SpecRegistryImpl<>(ExtensionDefinition.class);
    }

    @Bean
    EntityService<ExtensionDefinition> extensionDefinitionEntityService(
        SearchableEntityRepository<ExtensionDefinitionEntity, ExtensionDefinition> repository
    ) {
        return new BaseEntityServiceImpl<ExtensionDefinition, ExtensionDefinitionEntity>() {};
    }
}
