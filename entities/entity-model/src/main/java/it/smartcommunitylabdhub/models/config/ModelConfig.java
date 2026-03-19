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

package it.smartcommunitylabdhub.models.config;

import it.smartcommunitylabdhub.core.repositories.BaseEntityRepositoryImpl;
import it.smartcommunitylabdhub.core.repositories.SearchableEntityRepository;
import it.smartcommunitylabdhub.core.services.BaseEntityServiceImpl;
import it.smartcommunitylabdhub.core.services.BaseVersionableEntityServiceImpl;
import it.smartcommunitylabdhub.core.services.EntityService;
import it.smartcommunitylabdhub.core.specs.SpecRegistryImpl;
import it.smartcommunitylabdhub.extensions.ExtensibleEntityService;
import it.smartcommunitylabdhub.files.base.BaseFilesService;
import it.smartcommunitylabdhub.fsm.Fsm;
import it.smartcommunitylabdhub.lifecycle.BaseLifecycleManager;
import it.smartcommunitylabdhub.lifecycle.LifecycleManager;
import it.smartcommunitylabdhub.models.Model;
import it.smartcommunitylabdhub.models.lifecycle.ModelFsmFactoryBuilder;
import it.smartcommunitylabdhub.models.persistence.ModelEntity;
import it.smartcommunitylabdhub.models.persistence.ModelRepository;
import it.smartcommunitylabdhub.relationships.BaseRelationshipsAwareEntityService;
import it.smartcommunitylabdhub.search.base.BaseIndexableEntityService;
import it.smartcommunitylabdhub.search.indexers.EntityIndexer;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

@Configuration
public class ModelConfig {

    @Bean
    SearchableEntityRepository<ModelEntity, Model> modelSearchableEntityRepository(
        ModelRepository repository,
        Converter<Model, ModelEntity> entityBuilder,
        Converter<ModelEntity, Model> dtoBuilder
    ) {
        return new BaseEntityRepositoryImpl<>(repository, entityBuilder, dtoBuilder) {};
    }

    @Bean
    Fsm.Factory<String, String, Model> modelFsmFactory(ModelFsmFactoryBuilder builder) {
        return builder.build();
    }

    @Bean
    LifecycleManager<Model> modelLifecycleManager() {
        return new BaseLifecycleManager<Model>(Model.class) {};
    }

    @Bean
    EntityService<Model> modelEntityService(SearchableEntityRepository<ModelEntity, Model> repository) {
        BaseEntityServiceImpl<Model, ModelEntity> base = new BaseEntityServiceImpl<Model, ModelEntity>() {};
        base.setRepository(repository);
        return new ExtensibleEntityService<>(base);
    }

    @Bean
    SpecRegistryImpl<Model> modelSpecRegistry() {
        return new SpecRegistryImpl<>(Model.class);
    }

    @Bean
    BaseFilesService<Model> modelFilesService() {
        return new BaseFilesService<Model>() {};
    }

    @Bean
    BaseIndexableEntityService<Model> modelIndexableEntityService() {
        return new BaseIndexableEntityService<>() {};
    }

    // build indexer only if a provider is available
    // NOTE: we can not use ConditionalOnBean on the EntityIndexer.Factory because
    // the optional bean could be missing when this is processed
    @Bean
    EntityIndexer<Model> modelEntityIndexer(Optional<EntityIndexer.Factory> entityIndexerFactory) {
        return entityIndexerFactory.map(factory -> factory.build(Model.class)).orElse(null);
    }

    @Bean
    BaseRelationshipsAwareEntityService<Model> modelRelationshipsAwareEntityService() {
        return new BaseRelationshipsAwareEntityService<>() {};
    }

    @Bean
    BaseVersionableEntityServiceImpl<Model, ModelEntity> modelVersionableEntityService() {
        return new BaseVersionableEntityServiceImpl<>() {};
    }
}
