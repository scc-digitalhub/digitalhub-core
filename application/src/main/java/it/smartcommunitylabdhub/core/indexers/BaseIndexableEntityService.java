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

package it.smartcommunitylabdhub.core.indexers;

import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.metadata.MetadataDTO;
import it.smartcommunitylabdhub.core.persistence.BaseEntity;
import it.smartcommunitylabdhub.core.services.EntityService;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.Assert;

@Transactional
@Slf4j
public class BaseIndexableEntityService<E extends BaseEntity, D extends BaseDTO & MetadataDTO>
    implements IndexableEntityService<E>, InitializingBean {

    protected EntityService<D, E> entityService;
    protected Converter<D, E> entityBuilder;
    private EntityIndexer<E> indexer;

    @Autowired(required = false)
    public void setIndexer(EntityIndexer<E> indexer) {
        this.indexer = indexer;
    }

    @Autowired
    public void setEntityService(EntityService<D, E> entityService) {
        this.entityService = entityService;
    }

    @Autowired
    public void setEntityBuilder(Converter<D, E> entityBuilder) {
        this.entityBuilder = entityBuilder;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(entityBuilder, "builder can not be null");
        Assert.notNull(entityService, "entity service can not be null");
    }

    public void indexOne(@NotNull String id) throws NoSuchEntityException, SystemException {
        if (indexer != null) {
            log.debug("index with id {}", String.valueOf(id));
            try {
                D dto = entityService.get(id);
                indexer.index(entityBuilder.convert(dto));
            } catch (StoreException e) {
                log.error("store error: {}", e.getMessage());
                throw new SystemException(e.getMessage());
            }
        }
    }

    @Async
    @Override
    public void reindexAll() {
        if (indexer != null) {
            log.debug("reindex all");

            //clear index
            indexer.clearIndex();

            //use pagination and batch
            boolean hasMore = true;
            int pageNumber = 0;
            while (hasMore) {
                hasMore = false;

                try {
                    Page<D> page = entityService.list(PageRequest.of(pageNumber, EntityIndexer.PAGE_MAX_SIZE));
                    indexer.indexAll(
                        page.getContent().stream().map(e -> entityBuilder.convert(e)).collect(Collectors.toList())
                    );
                    hasMore = page.hasNext();
                } catch (IllegalArgumentException | StoreException | SystemException e) {
                    hasMore = false;

                    log.error("error with indexing: {}", e.getMessage());
                }
            }
        }
    }
}
