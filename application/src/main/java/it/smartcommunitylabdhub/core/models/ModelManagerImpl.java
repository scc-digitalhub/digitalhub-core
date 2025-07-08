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

package it.smartcommunitylabdhub.core.models;

import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.model.Model;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.commons.services.ModelManager;
import it.smartcommunitylabdhub.commons.services.VersionableEntityService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindException;

@Service
@Transactional
@Slf4j
public class ModelManagerImpl implements ModelManager {

    @Autowired
    private EntityService<Model> entityService;

    @Autowired
    private VersionableEntityService<Model> versionableService;

    @Override
    public Page<Model> listModels(Pageable pageable) {
        log.debug("list models page {}", pageable);

        try {
            return entityService.list(pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Model> listLatestModels() {
        log.debug("list latest models");

        try {
            return versionableService.listLatest();
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Model> listLatestModels(Pageable pageable) {
        log.debug("list latest models page {}", pageable);
        try {
            return versionableService.listLatest(pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Model> listModelsByUser(@NotNull String user) {
        log.debug("list all models for user {}  ", user);

        try {
            return entityService.listByUser(user);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Model> searchModels(Pageable pageable, @Nullable SearchFilter<Model> filter) {
        log.debug("search models page {}, filter {}", pageable, String.valueOf(filter));

        try {
            return entityService.search(filter, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Model> searchLatestModels(Pageable pageable, @Nullable SearchFilter<Model> filter) {
        log.debug("search latest models with {} page {}", String.valueOf(filter), pageable);

        try {
            return versionableService.searchLatest(filter, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Model> listModelsByProject(@NotNull String project) {
        log.debug("list all models for project {}  ", project);

        try {
            return entityService.listByProject(project);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Model> listModelsByProject(@NotNull String project, Pageable pageable) {
        log.debug("list all models for project {}  page {}", project, pageable);
        try {
            return entityService.listByProject(project, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Model> listLatestModelsByProject(@NotNull String project) {
        log.debug("list models for project {}  ", project);

        try {
            return versionableService.listLatestByProject(project);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Model> listLatestModelsByProject(@NotNull String project, Pageable pageable) {
        log.debug("list models for project {}  page {}", project, pageable);

        try {
            return versionableService.listLatestByProject(project, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Model> searchModelsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<Model> filter
    ) {
        log.debug("search all models for project {} with {} page {}", project, String.valueOf(filter), pageable);

        try {
            return entityService.searchByProject(project, filter, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Model> searchLatestModelsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<Model> filter
    ) {
        log.debug("search latest models for project {} with {} page {}", project, String.valueOf(filter), pageable);

        try {
            return versionableService.searchLatestByProject(project, filter, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Model> findModels(@NotNull String project, @NotNull String name) {
        log.debug("find models for project {} with name {}", project, name);

        try {
            return versionableService.findAll(project, name);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Model> findModels(@NotNull String project, @NotNull String name, Pageable pageable) {
        log.debug("find models for project {} with name {} page {}", project, name, pageable);

        try {
            return versionableService.findAll(project, name, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Model findModel(@NotNull String id) {
        log.debug("find model with id {}", String.valueOf(id));
        try {
            return entityService.find(id);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Model getModel(@NotNull String id) throws NoSuchEntityException {
        log.debug("get model with id {}", String.valueOf(id));

        try {
            return entityService.get(id);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.MODEL.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Model getLatestModel(@NotNull String project, @NotNull String name) throws NoSuchEntityException {
        log.debug("get latest model for project {} with name {}", project, name);

        try {
            return versionableService.getLatest(project, name);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Model createModel(@NotNull Model dto)
        throws DuplicatedEntityException, BindException, IllegalArgumentException {
        log.debug("create model");

        try {
            return entityService.create(dto);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Model updateModel(@NotNull String id, @NotNull Model dto)
        throws NoSuchEntityException, IllegalArgumentException, BindException {
        log.debug("update model with id {}", String.valueOf(id));
        try {
            return entityService.update(id, dto);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.MODEL.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteModel(@NotNull String id, @Nullable Boolean cascade) {
        log.debug("delete model with id {}", String.valueOf(id));

        try {
            entityService.delete(id, cascade);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteModels(@NotNull String project, @NotNull String name, @Nullable Boolean cascade) {
        log.debug("delete models for project {} with name {}", project, name);
        try {
            versionableService.deleteAll(project, name, cascade);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteModelsByProject(@NotNull String project, @Nullable Boolean cascade) {
        log.debug("delete models for project {}", project);
        try {
            entityService.deleteByProject(project, cascade);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }
}
