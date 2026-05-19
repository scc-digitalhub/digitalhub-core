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

package it.smartcommunitylabdhub.runtimes.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.runtimes.persistence.RunnableEntity;
import it.smartcommunitylabdhub.runtimes.persistence.RunnableRepository;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ResolvableType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

@Slf4j
public class RunnableStoreImpl<T extends RunRunnable> implements RunnableStore<T> {

    private final Class<T> clazz;
    private final RunnableRepository runnableRepository;
    private final TransactionTemplate transactionTemplate;
    private ObjectMapper objectMapper;

    public RunnableStoreImpl(
            Class<T> clazz,
            RunnableRepository runnableRepository,
            PlatformTransactionManager transactionManager) {
        this.clazz = clazz;
        this.runnableRepository = runnableRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // use CBOR mapper as default
        this.objectMapper = JacksonMapper.CBOR_OBJECT_MAPPER;

        log.debug("Initialized store for {}", clazz.getSimpleName());
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        Assert.notNull(objectMapper, "object mapper can not be null");
        this.objectMapper = objectMapper;
    }

    @Override
    @Cacheable(cacheResolver = "resolvableTypeCacheResolver", value = "store.find", key = "#id", unless = "#result == null")
    public T find(String id) throws StoreException {
        log.debug("find runnable {} with id {}", clazz.getName(), id);

        RunnableEntity runnableEntity = runnableRepository.find(clazz.getName(), id);
        if (runnableEntity == null) {
            return null;
        }

        try {
            return objectMapper.readValue(runnableEntity.getData(), clazz);
        } catch (IOException ex) {
            // Handle serialization error
            log.error("error deserializing runnable: {}", ex.getMessage());
            throw new StoreException("error deserializing runnable");
        }
    }

    @Override
    public List<T> findAll() {
        log.debug("find all runnable {}", clazz.getName());

        List<RunnableEntity> entities = runnableRepository.findAll(clazz.getName());
        return entities
                .stream()
                .map(entity -> {
                    try {
                        return objectMapper.readValue(entity.getData(), clazz);
                    } catch (IOException e) {
                        // Handle deserialization error
                        log.error("error deserializing runnable: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    @CacheEvict(cacheResolver = "resolvableTypeCacheResolver", value = "store.find", key = "#id")
    public void store(String id, T e) throws StoreException {
        log.debug("store runnable {} with id {}", clazz.getName(), id);
        try {
            byte[] data = objectMapper.writeValueAsBytes(e);
            RunnableEntity entity = RunnableEntity.builder().id(id).user(e.getUser()).data(data).build();

            transactionTemplate.executeWithoutResult(status -> runnableRepository.upsert(clazz.getName(), entity));
        } catch (IOException ex) {
            // Handle serialization error
            log.error("error serializing runnable: {}", ex.getMessage());
            throw new StoreException("error serializing runnable");
        }
    }

    @Override
    @CacheEvict(cacheResolver = "resolvableTypeCacheResolver", value = "store.find", key = "#id")
    public void remove(String id) throws StoreException {
        log.debug("remove runnable {} with id {}", clazz.getName(), id);

        transactionTemplate.executeWithoutResult(status -> runnableRepository.delete(clazz.getName(), id));
    }

    @Override
    public ResolvableType getResolvableType() {
        return ResolvableType.forClass(this.clazz);
    }
}
