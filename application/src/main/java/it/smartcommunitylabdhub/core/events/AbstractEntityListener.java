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

package it.smartcommunitylabdhub.core.events;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.components.cloud.CloudEntityEvent;
import it.smartcommunitylabdhub.components.websocket.UserNotificationEntityEvent;
import it.smartcommunitylabdhub.core.persistence.BaseEntity;
import it.smartcommunitylabdhub.relationships.EntityRelationshipsManager;
import it.smartcommunitylabdhub.search.indexers.EntityIndexer;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.convert.converter.Converter;

@Slf4j
public abstract class AbstractEntityListener<E extends BaseEntity, D extends BaseDTO> {

    protected final Converter<E, D> converter;
    protected ApplicationEventPublisher eventPublisher;
    protected final Class<D> clazz;

    protected EntityIndexer<D> indexer;
    protected EntityRelationshipsManager<D> relationshipsManager;

    protected AbstractEntityListener(Converter<E, D> converter) {
        this.converter = converter;
        clazz = extractClass();
    }

    @Autowired
    public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Autowired(required = false)
    public void setIndexer(EntityIndexer<D> indexer) {
        this.indexer = indexer;
    }

    @Autowired(required = false)
    public void setRelationshipsManager(EntityRelationshipsManager<D> manager) {
        this.relationshipsManager = manager;
    }

    protected void handle(EntityEvent<E> event) {
        log.debug("receive event for {} {}", clazz.getSimpleName(), event.getAction());

        E entity = event.getEntity();
        if (log.isTraceEnabled()) {
            log.trace("{}: {}", clazz.getSimpleName(), String.valueOf(entity));
        }

        if (entity == null) {
            return;
        }

        D dto = converter.convert(entity);
        switch (event.getAction()) {
            case CREATE:
                {
                    onCreate(entity, dto);
                    break;
                }
            case UPDATE:
                {
                    onUpdate(entity, dto);
                    break;
                }
            case DELETE:
                {
                    onDelete(entity, dto);
                    break;
                }
            default:
                break;
        }
    }

    protected void broadcast(EntityEvent<E> event) {
        log.debug("broadcast event for {} {}", clazz.getSimpleName(), event.getAction());
        //publish external event
        if (eventPublisher != null) {
            E entity = event.getEntity();
            if (log.isTraceEnabled()) {
                log.trace("{}: {}", clazz.getSimpleName(), String.valueOf(entity));
            }

            if (entity == null) {
                return;
            }

            D dto = converter.convert(entity);

            log.debug("publish cloud event: {} for {} {}", event.getAction(), clazz.getSimpleName(), dto.getId());
            CloudEntityEvent<D> cloud = new CloudEntityEvent<>(dto, clazz, event.getAction());
            if (log.isTraceEnabled()) {
                log.trace("cloud event: {}", String.valueOf(cloud));
            }

            eventPublisher.publishEvent(cloud);
        }
    }

    protected void notify(String user, EntityEvent<E> event) {
        log.debug("notify event for {} {}", clazz.getSimpleName(), event.getAction());
        //publish external event
        if (eventPublisher != null) {
            E entity = event.getEntity();
            if (log.isTraceEnabled()) {
                log.trace("{}: {}", clazz.getSimpleName(), String.valueOf(entity));
            }

            if (entity == null) {
                return;
            }

            D dto = converter.convert(entity);

            log.debug("publish notify event: {} for {} {}", event.getAction(), clazz.getSimpleName(), dto.getId());
            UserNotificationEntityEvent<D> cloud = new UserNotificationEntityEvent<>(
                user,
                dto,
                clazz,
                event.getAction()
            );
            if (log.isTraceEnabled()) {
                log.trace("notify event: {}", String.valueOf(cloud));
            }

            eventPublisher.publishEvent(cloud);
        }
    }

    protected void onCreate(E entity, D dto) {
        log.debug("onCreate for {}", entity.getId());
        //index
        if (indexer != null) {
            try {
                log.debug("index document with id {}", dto.getId());
                indexer.index(dto);
            } catch (Exception e) {
                log.error("error with solr: {}", e.getMessage());
            }
        }

        //relationships
        if (relationshipsManager != null) {
            try {
                log.debug("set relationship for entity with id {}", dto.getId());
                relationshipsManager.register(dto);
            } catch (StoreException e) {
                log.error("error with relationshipsManager: {}", e.getMessage());
            }
        }
    }

    protected void onUpdate(E entity, D dto) {
        log.debug("onUpdate for {}", entity.getId());
        //index
        if (indexer != null) {
            try {
                log.debug("index document with id {}", dto.getId());
                indexer.index(dto);
            } catch (Exception e) {
                log.error("error with solr: {}", e.getMessage());
            }
        }

        //relationships
        if (relationshipsManager != null) {
            try {
                log.debug("set relationship for entity with id {}", dto.getId());
                relationshipsManager.register(dto);
            } catch (StoreException e) {
                log.error("error with relationshipsManager: {}", e.getMessage());
            }
        }
    }

    protected void onDelete(E entity, D dto) {
        log.debug("onDelete for {}", entity.getId());

        if (indexer != null) {
            try {
                log.debug("remove index for entity with id {}", dto.getId());
                indexer.remove(dto);
            } catch (Exception e) {
                log.error("error with indexer: {}", e.getMessage());
            }
        }

        //relationships
        if (relationshipsManager != null) {
            try {
                log.debug("clear relationship for entity with id {}", dto.getId());
                relationshipsManager.clear(dto);
            } catch (StoreException e) {
                log.error("error with relationshipsManager: {}", e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected Class<D> extractClass() {
        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[1];
        return (Class<D>) t;
    }
}
