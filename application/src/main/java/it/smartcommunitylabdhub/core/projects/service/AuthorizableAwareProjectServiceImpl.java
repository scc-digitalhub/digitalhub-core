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
import it.smartcommunitylabdhub.authorization.services.AuthorizableAwareEntityService;
import it.smartcommunitylabdhub.authorization.services.ResourceSharingService;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.core.projects.persistence.ProjectEntity;
import it.smartcommunitylabdhub.core.queries.specifications.CommonSpecification;
import it.smartcommunitylabdhub.core.repositories.SearchableEntityRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@Transactional
@Slf4j
public class AuthorizableAwareProjectServiceImpl implements AuthorizableAwareEntityService<Project> {

    @Autowired
    private SearchableEntityRepository<ProjectEntity, Project> entityRepository;

    @Autowired
    private ResourceSharingService sharingService;

    @Override
    @Cacheable("findIdByCreatedBy")
    public List<String> findIdsByCreatedBy(@NotNull String createdBy) {
        log.debug("find id of projects for createdBy {}", createdBy);
        try {
            return entityRepository
                .searchAll(CommonSpecification.createdByEquals(createdBy))
                .stream()
                .map(p -> p.getId())
                .toList();
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    @Cacheable("findIdByUpdatedBy")
    public List<String> findIdsByUpdatedBy(@NotNull String updatedBy) {
        log.debug("find id of projects for updatedBy {}", updatedBy);
        try {
            return entityRepository
                .searchAll(CommonSpecification.updatedByEquals(updatedBy))
                .stream()
                .map(p -> p.getId())
                .toList();
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    @Cacheable("findIdByProject")
    public List<String> findIdsByProject(@NotNull String project) {
        log.debug("find id of projects for project {}", project);
        try {
            Project p = entityRepository.find(project);
            if (p == null) {
                return Collections.emptyList();
            }

            return Collections.singletonList(p.getId());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    @Cacheable("findNameByCreatedBy")
    public List<String> findNamesByCreatedBy(@NotNull String createdBy) {
        log.debug("find name of projects for createdBy {}", createdBy);
        try {
            return entityRepository
                .searchAll(CommonSpecification.createdByEquals(createdBy))
                .stream()
                .map(p -> p.getName())
                .toList();
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    @Cacheable("findNameByUpdatedBy")
    public List<String> findNamesByUpdatedBy(@NotNull String updatedBy) {
        log.debug("find name of projects for updatedBy {}", updatedBy);
        try {
            return entityRepository
                .searchAll(CommonSpecification.updatedByEquals(updatedBy))
                .stream()
                .map(p -> p.getName())
                .toList();
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    @Cacheable("findNameByProject")
    public List<String> findNamesByProject(@NotNull String project) {
        log.debug("find name of projects for project {}", project);
        try {
            Project p = entityRepository.find(project);
            if (p == null) {
                return Collections.emptyList();
            }

            return Collections.singletonList(p.getName());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    @Cacheable("findIdsBySharedTo")
    public List<String> findIdsBySharedTo(@NotNull String user) {
        log.debug("find ids of projects shared to {}", user);
        try {
            List<ResourceShareEntity> shares = sharingService
                .listByUser(user)
                .stream()
                .filter(s -> EntityName.PROJECT.getValue().equals(s.getEntity()))
                .toList();

            //for every project check if owner matches
            //DISABLED, we expect shares to be valid
            return shares
                .stream()
                .map(s -> {
                    try {
                        Project p = entityRepository.find(s.getEntityId());
                        // if (p != null && p.getUser() != null && p.getUser().equals(s.getOwner())) {
                        return p;
                        // }
                    } catch (StoreException e) {
                        log.error("store error: {}", e.getMessage());
                    }

                    return null;
                })
                .filter(p -> p != null)
                .map(p -> p.getId())
                .toList();
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    @Cacheable("findNamesBySharedTo")
    public List<String> findNamesBySharedTo(@NotNull String user) {
        log.debug("find name of projects shared to {}", user);
        try {
            List<ResourceShareEntity> shares = sharingService
                .listByUser(user)
                .stream()
                .filter(s -> EntityName.PROJECT.getValue().equals(s.getEntity()))
                .toList();

            //for every project check if owner matches
            //DISABLED, we expect shares to be valid
            return shares
                .stream()
                .map(s -> {
                    try {
                        Project p = entityRepository.find(s.getEntityId());
                        // if (p != null && p.getUser() != null && p.getUser().equals(s.getOwner())) {
                        return p;
                        // }
                    } catch (StoreException e) {
                        log.error("store error: {}", e.getMessage());
                    }

                    return null;
                })
                .filter(p -> p != null)
                .map(p -> p.getName())
                .toList();
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }
}
