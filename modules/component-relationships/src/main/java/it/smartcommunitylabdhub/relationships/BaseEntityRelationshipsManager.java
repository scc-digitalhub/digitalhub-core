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

package it.smartcommunitylabdhub.relationships;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.metadata.MetadataDTO;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.relationships.persistence.RelationshipEntity;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

@Slf4j
public class BaseEntityRelationshipsManager<D extends BaseDTO & MetadataDTO>
    implements EntityRelationshipsManager<D>, InitializingBean {

    protected final EntityName type;
    protected EntityRelationshipsService service;

    @Autowired
    public void setService(EntityRelationshipsService service) {
        this.service = service;
    }

    @SuppressWarnings("unchecked")
    public BaseEntityRelationshipsManager() {
        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.type = EntityUtils.getEntityName((Class<D>) t);
    }

    @SuppressWarnings("unchecked")
    public BaseEntityRelationshipsManager(EntityRelationshipsService service) {
        Assert.notNull(service, "relationship service can not be null");

        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.type = EntityUtils.getEntityName((Class<D>) t);
        this.service = service;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(service, "relationships service can not be null");
    }

    @Override
    public String toString() {
        return String.format("%s[type=%s]", this.getClass().getSimpleName(), type);
    }

    protected EntityName getType() {
        return type;
    }

    @Override
    public void register(D item) {
        Assert.notNull(item, "entity can not be null");

        try {
            log.debug("register for {}: {}", getType(), item.getId());

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
    public void clear(D item) {
        Assert.notNull(item, "entity can not be null");

        try {
            log.debug("clear for {}: {}", getType(), item.getId());

            service.clear(item.getProject(), getType(), item.getId());
        } catch (StoreException e) {
            log.error("error with service: {}", e.getMessage());
        }
    }

    @Override
    public List<RelationshipDetail> getRelationships(D item) throws StoreException {
        Assert.notNull(item, "entity can not be null");

        log.debug("get for {}: {}", getType(), item.getId());

        List<RelationshipEntity> entries = service.listByEntity(item.getProject(), getType(), item.getId());
        return entries
            .stream()
            .map(e -> new RelationshipDetail(e.getRelationship(), e.getSourceKey(), e.getDestKey()))
            .toList();
    }
}
