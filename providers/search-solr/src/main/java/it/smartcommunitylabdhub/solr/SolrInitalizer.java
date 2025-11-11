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

package it.smartcommunitylabdhub.solr;

import it.smartcommunitylabdhub.search.service.IndexableEntityService;
import it.smartcommunitylabdhub.solr.config.SolrProperties;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "solr", name = "url")
public class SolrInitalizer implements ApplicationListener<ApplicationStartedEvent> {

    private final SolrProperties properties;
    private List<IndexableEntityService<?>> services;

    public SolrInitalizer(SolrProperties solrProperties) {
        Assert.notNull(solrProperties, "solr properties are required");
        this.properties = solrProperties;
    }

    @Autowired(required = false)
    public void setServices(List<IndexableEntityService<?>> services) {
        this.services = services;
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        // trigger reindex if required
        if (services != null && "always".equals(properties.getReindex())) {
            //reindex
            services.forEach(service -> service.reindexAll());
        }
    }
}
