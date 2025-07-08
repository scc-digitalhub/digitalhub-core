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

package it.smartcommunitylabdhub.core.artifacts;

import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.artifact.Artifact;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.services.ArtifactManager;
import it.smartcommunitylabdhub.commons.services.EntityService;
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
public class ArtifactManagerImpl implements ArtifactManager {

    @Autowired
    private EntityService<Artifact> entityService;

    @Autowired
    private VersionableEntityService<Artifact> versionableService;

    @Override
    public Page<Artifact> listArtifacts(Pageable pageable) {
        log.debug("list artifacts page {}", pageable);

        try {
            return entityService.list(pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Artifact> listLatestArtifacts() {
        log.debug("list latest artifacts");

        try {
            return versionableService.listLatest();
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Artifact> listLatestArtifacts(Pageable pageable) {
        log.debug("list latest artifacts page {}", pageable);
        try {
            return versionableService.listLatest(pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Artifact> listArtifactsByUser(@NotNull String user) {
        log.debug("list all artifacts for user {}  ", user);

        try {
            return entityService.listByUser(user);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Artifact> searchArtifacts(Pageable pageable, @Nullable SearchFilter<Artifact> filter) {
        log.debug("search artifacts page {}, filter {}", pageable, String.valueOf(filter));

        try {
            return entityService.search(filter, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Artifact> searchLatestArtifacts(Pageable pageable, @Nullable SearchFilter<Artifact> filter) {
        log.debug("search latest artifacts with {} page {}", String.valueOf(filter), pageable);

        try {
            return versionableService.searchLatest(filter, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Artifact> listArtifactsByProject(@NotNull String project) {
        log.debug("list all artifacts for project {}  ", project);

        try {
            return entityService.listByProject(project);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Artifact> listArtifactsByProject(@NotNull String project, Pageable pageable) {
        log.debug("list all artifacts for project {}  page {}", project, pageable);

        try {
            return entityService.listByProject(project, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Artifact> listLatestArtifactsByProject(@NotNull String project) {
        log.debug("list artifacts for project {}  ", project);

        try {
            return versionableService.listLatestByProject(project);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Artifact> listLatestArtifactsByProject(@NotNull String project, Pageable pageable) {
        log.debug("list artifacts for project {}  page {}", project, pageable);

        try {
            return versionableService.listLatestByProject(project, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Artifact> searchArtifactsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<Artifact> filter
    ) {
        log.debug("search all artifacts for project {} with {} page {}", project, String.valueOf(filter), pageable);

        try {
            return entityService.searchByProject(project, filter, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Artifact> searchLatestArtifactsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<Artifact> filter
    ) {
        log.debug("search latest artifacts for project {} with {} page {}", project, String.valueOf(filter), pageable);

        try {
            return versionableService.searchLatestByProject(project, filter, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Artifact> findArtifacts(@NotNull String project, @NotNull String name) {
        log.debug("find artifacts for project {} with name {}", project, name);

        try {
            return versionableService.findAll(project, name);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Artifact> findArtifacts(@NotNull String project, @NotNull String name, Pageable pageable) {
        log.debug("find artifacts for project {} with name {} page {}", project, name, pageable);

        try {
            return versionableService.findAll(project, name, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Artifact findArtifact(@NotNull String id) {
        log.debug("find artifact with id {}", String.valueOf(id));
        try {
            return entityService.find(id);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Artifact getArtifact(@NotNull String id) throws NoSuchEntityException {
        log.debug("get artifact with id {}", String.valueOf(id));

        try {
            return entityService.get(id);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.ARTIFACT.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Artifact getLatestArtifact(@NotNull String project, @NotNull String name) throws NoSuchEntityException {
        log.debug("get latest artifact for project {} with name {}", project, name);

        try {
            return versionableService.getLatest(project, name);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Artifact createArtifact(@NotNull Artifact dto)
        throws DuplicatedEntityException, BindException, IllegalArgumentException {
        log.debug("create artifact");

        try {
            return entityService.create(dto);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Artifact updateArtifact(@NotNull String id, @NotNull Artifact dto)
        throws NoSuchEntityException, IllegalArgumentException, BindException {
        log.debug("update artifact with id {}", String.valueOf(id));
        try {
            return entityService.update(id, dto);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.ARTIFACT.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteArtifact(@NotNull String id, @Nullable Boolean cascade) {
        log.debug("delete artifact with id {}", String.valueOf(id));

        try {
            entityService.delete(id, cascade);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteArtifacts(@NotNull String project, @NotNull String name, @Nullable Boolean cascade) {
        log.debug("delete artifacts for project {} with name {}", project, name);
        try {
            versionableService.deleteAll(project, name, cascade);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteArtifactsByProject(@NotNull String project, @Nullable Boolean cascade) {
        log.debug("delete artifacts for project {}", project);
        try {
            entityService.deleteByProject(project, cascade);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }
}
