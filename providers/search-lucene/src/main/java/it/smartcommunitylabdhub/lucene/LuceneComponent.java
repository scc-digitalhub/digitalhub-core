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

package it.smartcommunitylabdhub.lucene;

import it.smartcommunitylabdhub.lucene.config.LuceneProperties;
import it.smartcommunitylabdhub.lucene.service.LuceneManager;
import it.smartcommunitylabdhub.search.indexers.IndexerException;
import it.smartcommunitylabdhub.search.indexers.ItemResult;
import it.smartcommunitylabdhub.search.indexers.SearchGroupResult;
import it.smartcommunitylabdhub.search.indexers.SearchPage;
import it.smartcommunitylabdhub.search.service.SearchService;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.util.Assert;

@Slf4j
public class LuceneComponent implements SearchService, SchedulingConfigurer, InitializingBean {

    private static final int DEFAULT_DELAY = 60; //every minute
    private static final int INITIAL_DELAY = 120; //wait 120s for start

    private LuceneManager indexManager;
    private LuceneProperties properties;

    public LuceneComponent(LuceneProperties properties) {
        Assert.notNull(properties, "lucene properties are required");
        this.properties = properties;

        if (log.isTraceEnabled()) {
            log.trace("properties: {}", properties);
        }

        //build manager
        this.indexManager = new LuceneManager(properties);
    }

    @Override
    public void afterPropertiesSet() {
        Assert.notNull(indexManager, "index manager missing");
        if (log.isTraceEnabled()) {
            log.trace("init lucene component");
        }
        try {
            //init
            indexManager.init();
        } catch (IndexerException e) {
            log.error("error initializing lucene: {}", e.getMessage());
            indexManager = null;
        }
    }

    @PreDestroy
    public void close() {
        if (log.isTraceEnabled()) {
            log.trace("close lucene component");
        }
        try {
            if (indexManager != null) {
                indexManager.close();
            }
        } catch (IndexerException e) {
            log.error("error disconnecting lucene: {}", e.getMessage());
        }
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        Integer interval =
            properties.getCommitInterval() != null && properties.getCommitInterval() > DEFAULT_DELAY
                ? properties.getCommitInterval()
                : DEFAULT_DELAY;
        registrar.addFixedDelayTask(
            new FixedDelayTask(this::scheduledCommit, Duration.ofSeconds(interval), Duration.ofSeconds(INITIAL_DELAY))
        );
    }

    public void scheduledCommit() {
        if (log.isTraceEnabled()) {
            log.trace("scheduled commit lucene index");
        }
        try {
            this.commit();
        } catch (IndexerException e) {
            log.error("error committing lucene index: {}", e.getMessage());
        }
    }

    /*
     * public API
     */

    public void commit() throws IndexerException {
        if (indexManager != null) {
            indexManager.commit();
        }
    }

    public void indexDoc(Document doc) throws IndexerException {
        Assert.notNull(doc, "doc can not be null");
        if (doc.getField("id") == null) {
            throw new IllegalArgumentException("missing or invalid id in doc");
        }

        if (doc.getField("type") == null) {
            throw new IllegalArgumentException("missing or invalid type in doc");
        }
        if (indexManager != null) {
            log.debug("index doc {}", doc.getField("id").stringValue());

            if (log.isTraceEnabled()) {
                log.trace("index doc: {}", doc);
            }
            indexManager.indexDoc(doc);
        }
    }

    public void removeDoc(String id) throws IndexerException {
        Assert.notNull(id, "id can not be null");
        if (indexManager != null) {
            log.debug("remove doc {}", id);

            indexManager.removeDoc(id);
        }
    }

    public void indexBounce(Iterable<Document> docs) throws IndexerException {
        Assert.notNull(docs, "docs can not be null");
        if (indexManager != null) {
            log.debug("index bounce docs");
            if (log.isTraceEnabled()) {
                log.trace("index bounce docs: {}", docs);
            }

            indexManager.indexBounce(docs);
        }
    }

    //    public void registerFields(Iterable<IndexField> fields) throws LuceneIndexerException {
    //        Assert.notNull(fields, "fields can not be null");
    //        if (indexManager != null) {
    //            indexManager.initFields(fields);
    //        }
    //    }

    @Override
    public SearchPage<SearchGroupResult> groupSearch(String q, List<String> fq, Pageable pageRequest)
        throws IndexerException {
        if (indexManager == null) {
            throw new IndexerException("lucene not available");
        }
        return indexManager.groupSearch(q, fq, pageRequest);
    }

    @Override
    public SearchPage<ItemResult> itemSearch(String q, List<String> fq, Pageable pageRequest) throws IndexerException {
        if (indexManager == null) {
            throw new IndexerException("lucene not available");
        }
        return indexManager.itemSearch(q, fq, pageRequest);
    }

    public void clearIndex() throws IndexerException {
        if (indexManager != null) {
            log.debug("clear index");
            indexManager.clearIndex();
        }
    }

    public void clearIndexByType(String type) throws IndexerException {
        Assert.notNull(type, "type is required");
        if (indexManager != null) {
            log.debug("clear index by type {}", type);
            indexManager.clearIndexByType(type);
        }
    }
}
