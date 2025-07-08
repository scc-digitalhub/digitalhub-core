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

import it.smartcommunitylabdhub.commons.accessors.fields.KeyAccessor;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.core.relationships.persistence.RelationshipEntity;
import it.smartcommunitylabdhub.core.relationships.persistence.RelationshipRepository;
import it.smartcommunitylabdhub.core.utils.UUIDKeyGenerator;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
@Transactional
@Slf4j
public class EntityRelationshipsService {

    private final RelationshipRepository repository;
    private StringKeyGenerator keyGenerator = new UUIDKeyGenerator();

    public EntityRelationshipsService(RelationshipRepository repository) {
        Assert.notNull(repository, "relationships repository can not be null");
        this.repository = repository;
    }

    @Autowired(required = false)
    public void setKeyGenerator(StringKeyGenerator keyGenerator) {
        Assert.notNull(keyGenerator, "key generator can not be null");
        this.keyGenerator = keyGenerator;
    }

    @Transactional(readOnly = true)
    public List<RelationshipEntity> listByProject(@NotNull String project) throws StoreException {
        log.debug("list relationships for project {}", project);

        return repository.findByProject(project);
    }

    @Transactional(readOnly = true)
    public List<RelationshipEntity> listByProjectAndType(@NotNull String project, @NotNull EntityName entityName)
        throws StoreException {
        log.debug("list relationships for project {} entity type {}", project, entityName);

        return repository.findByProjectAndEntity(project, entityName.getValue());
    }

    @Transactional(readOnly = true)
    public List<RelationshipEntity> listByEntity(
        @NotNull String project,
        @NotNull EntityName entityName,
        @NotNull String id
    ) throws StoreException {
        log.debug("list relationships for project {} entity type {} id {}", project, entityName, String.valueOf(id));

        return repository.findByProjectAndEntityId(project, entityName.getValue(), id);
    }

    public List<RelationshipEntity> register(
        @NotNull String project,
        @NotNull EntityName entityName,
        @NotNull String id,
        @NotNull String key,
        @NotNull List<RelationshipDetail> relationships
    ) throws StoreException {
        log.debug("set relationships for project {} entity type {} id {}", project, entityName, String.valueOf(id));

        if (log.isTraceEnabled()) {
            log.trace("relationships: {}", relationships);
        }

        List<RelationshipEntity> result = new ArrayList<>();

        //update if needed
        List<RelationshipEntity> current = repository.findByProjectAndSourceEntityId(
            project,
            entityName.getValue(),
            id
        );
        List<RelationshipEntity> toKeep = current
            .stream()
            .filter(r ->
                relationships
                    .stream()
                    .anyMatch(e -> e.getDest().equals(r.getDestKey()) && e.getType().equals(r.getRelationship()))
            )
            .toList();
        List<RelationshipEntity> toRemove = current
            .stream()
            .filter(r -> toKeep.stream().noneMatch(e -> e.getId().equals(r.getId())))
            .toList();

        List<RelationshipDetail> toAdd = relationships
            .stream()
            .filter(r ->
                toKeep
                    .stream()
                    .noneMatch(e -> e.getDestKey().equals(r.getDest()) && e.getRelationship().equals(r.getType()))
            )
            .toList();

        //remove stale
        if (!toRemove.isEmpty()) {
            repository.deleteAllInBatch(toRemove);
        }

        //add new
        for (RelationshipDetail r : toAdd) {
            result.add(register(project, entityName, id, key, r));
        }

        return result;
    }

    public RelationshipEntity register(
        @NotNull String project,
        @NotNull EntityName entityName,
        @NotNull String id,
        @NotNull String key,
        @NotNull RelationshipDetail relationship
    ) throws StoreException {
        log.debug("register relationship for project {} entity type {} id {}", project, entityName, String.valueOf(id));

        if (log.isTraceEnabled()) {
            log.trace("relationship: {}", relationship);
        }

        RelationshipEntity entity = new RelationshipEntity();
        entity.setId(keyGenerator.generateKey());
        entity.setProject(project);

        //rel
        entity.setRelationship(relationship.getType());

        //source
        entity.setSourceId(id);
        entity.setSourceType(entityName.getValue());
        entity.setSourceKey(key);

        //dest
        KeyAccessor dest = KeyAccessor.with(relationship.getDest());
        if (dest.getId() == null) {
            throw new IllegalArgumentException("invalid destination");
        }

        entity.setDestId(dest.getId());
        entity.setDestKey(relationship.getDest());
        entity.setDestType(dest.getType());

        entity = repository.save(entity);

        return entity;
    }

    public void clear(@NotNull String project, @NotNull EntityName entityName, @NotNull String id)
        throws StoreException {
        log.debug("clear relationships for project {} entity type {} id {}", project, entityName, String.valueOf(id));

        List<RelationshipEntity> list = repository.findByProjectAndEntityId(project, entityName.getValue(), id);
        if (!list.isEmpty()) {
            if (log.isTraceEnabled()) {
                log.trace("relationships: {}", list);
            }

            //clear in batch
            repository.deleteAllInBatch(list);
        }
    }
}
