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

package it.smartcommunitylabdhub.dataitems.config;

import it.smartcommunitylabdhub.core.repositories.BaseEntityRepositoryImpl;
import it.smartcommunitylabdhub.core.repositories.SearchableEntityRepository;
import it.smartcommunitylabdhub.core.services.BaseEntityServiceImpl;
import it.smartcommunitylabdhub.core.services.BaseVersionableEntityServiceImpl;
import it.smartcommunitylabdhub.core.services.EntityService;
import it.smartcommunitylabdhub.core.specs.SpecRegistryImpl;
import it.smartcommunitylabdhub.dataitems.DataItem;
import it.smartcommunitylabdhub.dataitems.lifecycle.DataItemFsmFactoryBuilder;
import it.smartcommunitylabdhub.dataitems.persistence.DataItemEntity;
import it.smartcommunitylabdhub.dataitems.persistence.DataItemRepository;
import it.smartcommunitylabdhub.extensions.ExtensibleEntityService;
import it.smartcommunitylabdhub.files.base.BaseFilesService;
import it.smartcommunitylabdhub.fsm.Fsm;
import it.smartcommunitylabdhub.lifecycle.BaseLifecycleManager;
import it.smartcommunitylabdhub.lifecycle.LifecycleManager;
import it.smartcommunitylabdhub.relationships.BaseRelationshipsAwareEntityService;
import it.smartcommunitylabdhub.search.base.BaseIndexableEntityService;
import it.smartcommunitylabdhub.search.indexers.EntityIndexer;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

@Configuration
public class DataItemConfig {

    @Bean
    public SearchableEntityRepository<DataItemEntity, DataItem> dataItemSearchableEntityRepository(
        DataItemRepository repository,
        Converter<DataItem, DataItemEntity> entityBuilder,
        Converter<DataItemEntity, DataItem> dtoBuilder
    ) {
        return new BaseEntityRepositoryImpl<>(repository, entityBuilder, dtoBuilder) {};
    }

    @Bean
    Fsm.Factory<String, String, DataItem> dataItemFsmFactory(DataItemFsmFactoryBuilder builder) {
        return builder.build();
    }

    @Bean
    LifecycleManager<DataItem> dataItemLifecycleManager() {
        return new BaseLifecycleManager<DataItem>(DataItem.class) {};
    }

    @Bean
    EntityService<DataItem> dataItemEntityService(SearchableEntityRepository<DataItemEntity, DataItem> repository) {
        BaseEntityServiceImpl<DataItem, DataItemEntity> base = new BaseEntityServiceImpl<DataItem, DataItemEntity>() {};
        base.setRepository(repository);
        return new ExtensibleEntityService<>(base);
    }

    @Bean
    SpecRegistryImpl<DataItem> dataItemSpecRegistry() {
        return new SpecRegistryImpl<>(DataItem.class);
    }

    @Bean
    BaseFilesService<DataItem> dataItemFilesService() {
        return new BaseFilesService<DataItem>() {};
    }

    @Bean
    BaseIndexableEntityService<DataItem> dataItemIndexableEntityService() {
        return new BaseIndexableEntityService<>() {};
    }

    // build indexer only if a provider is available
    // NOTE: we can not use ConditionalOnBean on the EntityIndexer.Factory because
    // the optional bean could be missing when this is processed
    @Bean
    EntityIndexer<DataItem> dataItemEntityIndexer(Optional<EntityIndexer.Factory> entityIndexerFactory) {
        return entityIndexerFactory.map(factory -> factory.build(DataItem.class)).orElse(null);
    }

    @Bean
    BaseRelationshipsAwareEntityService<DataItem> dataItemRelationshipsAwareEntityService() {
        return new BaseRelationshipsAwareEntityService<>() {};
    }

    @Bean
    BaseVersionableEntityServiceImpl<DataItem, DataItemEntity> dataItemVersionableEntityService() {
        return new BaseVersionableEntityServiceImpl<>() {};
    }
}
