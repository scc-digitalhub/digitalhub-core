/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * Copyright 2025 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.core.projects.service;

import it.smartcommunitylabdhub.authorization.model.ResourceShareEntity;
import it.smartcommunitylabdhub.authorization.services.ResourceSharingService;
import it.smartcommunitylabdhub.authorization.services.ShareableAwareEntityService;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

@Service
@Transactional
@Slf4j
public class ShareableProjectService implements ShareableAwareEntityService<Project> {

    @Autowired
    private EntityRepository<Project> entityService;

    @Autowired
    private ResourceSharingService sharingService;

    @Override
    @CacheEvict(value = { "findIdsBySharedTo", "findNamesBySharedTo" }, allEntries = true)
    public ResourceShareEntity share(@NotNull String id, @NotNull String user) {
        log.debug("share project with id {} to {}", String.valueOf(id), String.valueOf(user));

        try {
            Project project = entityService.get(id);

            //check if a share with same user already exists
            List<ResourceShareEntity> shares = sharingService.listByProjectAndEntity(
                project.getProject(),
                EntityName.PROJECT,
                id,
                user
            );
            if (!shares.isEmpty()) {
                return shares.get(0);
            }

            //create
            ResourceShareEntity share = sharingService.share(project.getProject(), EntityName.PROJECT, id, user);

            if (log.isTraceEnabled()) {
                log.trace("share: {}", share);
            }

            return share;
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.PROJECT.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    @CacheEvict(value = { "findIdsBySharedTo", "findNamesBySharedTo" }, allEntries = true)
    public void revoke(@NotNull String id, @NotNull String shareId) {
        log.debug("revoke share project {} with id {}", String.valueOf(id), String.valueOf(shareId));

        try {
            Project project = entityService.get(id);
            ResourceShareEntity share = sharingService.get(shareId);

            if (share == null) {
                return;
            }

            //check project match
            if (!project.getId().equals(share.getProject())) {
                throw new IllegalArgumentException("project-mismatch");
            }
            if (!id.equals(share.getEntityId()) || !EntityName.PROJECT.getValue().equals(share.getEntity())) {
                throw new IllegalArgumentException("invalid");
            }

            //revoke
            sharingService.revoke(shareId);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.PROJECT.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<ResourceShareEntity> listSharesById(@NotNull String id) {
        log.debug("list shares for project with id {}", String.valueOf(id));
        try {
            return sharingService.listByProjectAndEntity(id, EntityName.PROJECT, id);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.PROJECT.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }
}
