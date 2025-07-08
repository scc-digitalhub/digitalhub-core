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

package it.smartcommunitylabdhub.authorization.services;

import it.smartcommunitylabdhub.authorization.model.ResourceShareEntity;
import it.smartcommunitylabdhub.authorization.repositories.ResourceShareRepository;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
@Slf4j
public class ResourceSharingService {

    private final ResourceShareRepository repository;

    public ResourceSharingService(ResourceShareRepository repository) {
        Assert.notNull(repository, "share repository can not be null");
        this.repository = repository;
    }

    public ResourceShareEntity share(
        @NotNull String project,
        @NotNull EntityName entityName,
        @NotNull String id,
        @NotNull String user
    ) throws StoreException {
        log.debug("create share for {}:{} to user {}", entityName, id, user);

        ResourceShareEntity share = ResourceShareEntity
            .builder()
            .id(UUID.randomUUID().toString().replace("-", ""))
            .project(project)
            .entity(entityName.getValue())
            .entityId(id)
            .user(user)
            .build();

        share = repository.saveAndFlush(share);

        if (log.isTraceEnabled()) {
            log.trace("share: ", share);
        }

        return share;
    }

    public ResourceShareEntity revoke(@NotNull String id) throws StoreException {
        log.debug("revoke share {}", id);
        Optional<ResourceShareEntity> res = repository.findById(id);
        if (res.isEmpty()) {
            return null;
        }

        ResourceShareEntity share = res.get();
        repository.delete(share);

        if (log.isTraceEnabled()) {
            log.trace("share: ", share);
        }

        return share;
    }

    public ResourceShareEntity get(@NotNull String id) throws StoreException {
        log.debug("get share {}", id);
        Optional<ResourceShareEntity> res = repository.findById(id);
        if (res.isEmpty()) {
            return null;
        }

        ResourceShareEntity share = res.get();
        if (log.isTraceEnabled()) {
            log.trace("share: ", share);
        }

        return share;
    }

    public List<ResourceShareEntity> listByProject(@NotNull String project) throws StoreException {
        return repository.findByProject(project);
    }

    public List<ResourceShareEntity> listByUser(@NotNull String user) throws StoreException {
        return repository.findByUser(user);
    }

    public List<ResourceShareEntity> listByOwner(@NotNull String owner) throws StoreException {
        return repository.findByOwner(owner);
    }

    public List<ResourceShareEntity> listByProjectAndOwner(@NotNull String project, @NotNull String owner)
        throws StoreException {
        return repository.findByProjectAndOwner(project, owner);
    }

    public List<ResourceShareEntity> listByProjectAndUser(@NotNull String project, @NotNull String user)
        throws StoreException {
        return repository.findByProjectAndUser(project, user);
    }

    public List<ResourceShareEntity> listByProjectAndEntity(
        @NotNull String project,
        @NotNull EntityName entity,
        @NotNull String id
    ) throws StoreException {
        return repository.findByProjectAndEntityAndEntityId(project, entity.getValue(), id);
    }

    public List<ResourceShareEntity> listByProjectAndEntity(
        @NotNull String project,
        @NotNull EntityName entity,
        @NotNull String id,
        @NotNull String user
    ) throws StoreException {
        return repository.findByProjectAndEntityAndEntityIdAndUser(project, entity.getValue(), id, user);
    }
}
