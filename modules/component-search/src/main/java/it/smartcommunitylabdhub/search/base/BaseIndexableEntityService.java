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

package it.smartcommunitylabdhub.search.base;

import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.search.indexers.EntityIndexer;
import it.smartcommunitylabdhub.search.service.IndexableEntityService;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Transactional(readOnly = true)
@Slf4j
public class BaseIndexableEntityService<D extends BaseDTO> implements IndexableEntityService<D>, InitializingBean {

    protected EntityRepository<D> entityService;
    private EntityIndexer<D> indexer;

    @Autowired(required = false)
    public void setIndexer(EntityIndexer<D> indexer) {
        this.indexer = indexer;
    }

    @Autowired
    public void setEntityService(EntityRepository<D> entityService) {
        this.entityService = entityService;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(entityService, "entity service can not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public void indexOne(@NotNull String id) throws NoSuchEntityException, SystemException {
        if (indexer != null) {
            log.debug("index with id {}", String.valueOf(id));
            try {
                D item = entityService.get(id);
                indexer.index(item);
            } catch (StoreException e) {
                log.error("store error: {}", e.getMessage());
                throw new SystemException(e.getMessage());
            }
        }
    }

    @Async
    @Override
    @Transactional(readOnly = true)
    public void reindexAll() {
        if (indexer != null) {
            log.debug("reindex all");

            //clear index
            indexer.clearIndex();

            //use pagination and batch
            boolean hasMore = false;
            int pageNumber = 0;
            do {
                try {
                    log.trace("index page {}", pageNumber);
                    Page<D> page = entityService.list(PageRequest.of(pageNumber, EntityIndexer.PAGE_MAX_SIZE));
                    indexer.indexAll(page.getContent());
                    hasMore = page.hasNext();
                    pageNumber++;
                } catch (IllegalArgumentException | StoreException | SystemException e) {
                    hasMore = false;
                    log.error("error with indexing: {}", e.getMessage());
                }
            } while (hasMore);
        }
    }
}
