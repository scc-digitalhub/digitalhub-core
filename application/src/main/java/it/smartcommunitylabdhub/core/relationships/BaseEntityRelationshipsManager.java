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

package it.smartcommunitylabdhub.core.relationships;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.metadata.MetadataDTO;
import it.smartcommunitylabdhub.commons.models.metadata.RelationshipsMetadata;
import it.smartcommunitylabdhub.commons.models.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.core.persistence.BaseEntity;
import it.smartcommunitylabdhub.core.relationships.persistence.RelationshipEntity;
import it.smartcommunitylabdhub.core.utils.EntityUtils;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.util.Assert;

@Slf4j
public class BaseEntityRelationshipsManager<E extends BaseEntity, D extends BaseDTO & MetadataDTO>
    implements EntityRelationshipsManager<E>, InitializingBean {

    protected final EntityName type;

    protected Converter<E, D> converter;
    protected EntityRelationshipsService service;

    @Autowired
    public void setConverter(Converter<E, D> converter) {
        this.converter = converter;
    }

    @Autowired
    public void setService(EntityRelationshipsService service) {
        this.service = service;
    }

    @SuppressWarnings("unchecked")
    public BaseEntityRelationshipsManager() {
        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[1];
        this.type = EntityUtils.getEntityName((Class<D>) t);
    }

    @SuppressWarnings("unchecked")
    public BaseEntityRelationshipsManager(EntityRelationshipsService service, Converter<E, D> converter) {
        Assert.notNull(converter, "converter can not be null");
        Assert.notNull(service, "relationship service can not be null");

        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[1];
        this.type = EntityUtils.getEntityName((Class<D>) t);
        this.converter = converter;
        this.service = service;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(converter, "converter can not be null");
        Assert.notNull(service, "relationships service can not be null");
    }

    protected EntityName getType() {
        return type;
    }

    @Override
    public void register(E entity) {
        Assert.notNull(entity, "entity can not be null");

        D item = converter.convert(entity);
        if (item == null) {
            throw new IllegalArgumentException("invalid or null entity");
        }
        try {
            log.debug("register for {}: {}", getType(), entity.getId());

            RelationshipsMetadata relationships = RelationshipsMetadata.from(item.getMetadata());
            service.register(
                item.getProject(),
                getType(),
                item.getId(),
                item.getKey(),
                relationships.getRelationships()
            );
        } catch (StoreException e) {
            log.error("error with service: {}", e.getMessage());
        }
    }

    @Override
    public void clear(E entity) {
        Assert.notNull(entity, "entity can not be null");

        D item = converter.convert(entity);
        if (item == null) {
            throw new IllegalArgumentException("invalid or null entity");
        }
        try {
            log.debug("clear for {}: {}", getType(), entity.getId());

            service.clear(item.getProject(), getType(), item.getId());
        } catch (StoreException e) {
            log.error("error with service: {}", e.getMessage());
        }
    }

    @Override
    public List<RelationshipDetail> getRelationships(E entity) throws StoreException {
        Assert.notNull(entity, "entity can not be null");

        log.debug("get for {}: {}", getType(), entity.getId());

        List<RelationshipEntity> entries = service.listByEntity(entity.getProject(), getType(), entity.getId());
        return entries
            .stream()
            .map(e -> new RelationshipDetail(e.getRelationship(), e.getSourceKey(), e.getDestKey()))
            .toList();
    }
}
