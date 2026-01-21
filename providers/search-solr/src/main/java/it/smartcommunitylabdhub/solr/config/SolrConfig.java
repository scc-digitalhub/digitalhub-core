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

package it.smartcommunitylabdhub.solr.config;

import it.smartcommunitylabdhub.commons.config.YamlPropertySourceFactory;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.search.indexers.EntityIndexer;
import it.smartcommunitylabdhub.solr.SolrComponent;
import it.smartcommunitylabdhub.solr.SolrInitalizer;
import it.smartcommunitylabdhub.solr.base.SolrBaseEntityIndexer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.annotation.Order;

@Configuration
@Order(3)
@PropertySource(value = "classpath:/search-solr.yml", factory = YamlPropertySourceFactory.class)
@EnableConfigurationProperties({ SolrProperties.class })
public class SolrConfig {

    @Bean
    @ConditionalOnProperty(prefix = "solr", name = "url")
    @Primary
    SolrComponent solrComponent(SolrProperties solrProperties) {
        return new SolrComponent(solrProperties);
    }

    @Bean
    @ConditionalOnBean(SolrComponent.class)
    EntityIndexer.Factory solrEntityIndexerSupplier(SolrComponent solr) {
        return new EntityIndexer.Factory() {
            @Override
            public <T extends BaseDTO> EntityIndexer<T> build(Class<T> clazz) {
                return new SolrBaseEntityIndexer<>(clazz, solr);
            }
        };
    }

    @Bean
    @ConditionalOnBean(SolrComponent.class)
    SolrInitalizer solrInitalizer(SolrProperties solrProperties, SolrComponent solr) {
        return new SolrInitalizer(solrProperties);
    }
}
