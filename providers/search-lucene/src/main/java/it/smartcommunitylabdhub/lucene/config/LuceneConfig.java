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

package it.smartcommunitylabdhub.lucene.config;

import it.smartcommunitylabdhub.commons.config.YamlPropertySourceFactory;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.lucene.LuceneComponent;
import it.smartcommunitylabdhub.lucene.LuceneInitalizer;
import it.smartcommunitylabdhub.lucene.base.LuceneBaseEntityIndexer;
import it.smartcommunitylabdhub.search.indexers.EntityIndexer;
import it.smartcommunitylabdhub.search.service.SearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.annotation.Order;

@Configuration
@Order(3)
@PropertySource(value = "classpath:/search-lucene.yml", factory = YamlPropertySourceFactory.class)
@EnableConfigurationProperties({ LuceneProperties.class })
public class LuceneConfig {

    @Bean
    @ConditionalOnProperty(prefix = "lucene", name = "index-path")
    @ConditionalOnMissingBean(value = SearchService.class, ignored = LuceneComponent.class)
    LuceneComponent luceneComponent(LuceneProperties luceneProperties) {
        return new LuceneComponent(luceneProperties);
    }

    @Bean
    @ConditionalOnBean(LuceneComponent.class)
    EntityIndexer.Factory luceneEntityIndexerSupplier(LuceneComponent lucene) {
        return new EntityIndexer.Factory() {
            @Override
            public <T extends BaseDTO> EntityIndexer<T> build(Class<T> clazz) {
                return new LuceneBaseEntityIndexer<>(clazz, lucene);
            }
        };
    }

    @Bean
    @ConditionalOnBean(LuceneComponent.class)
    LuceneInitalizer luceneInitalizer(LuceneProperties luceneProperties, LuceneComponent lucene) {
        return new LuceneInitalizer(luceneProperties);
    }
}
