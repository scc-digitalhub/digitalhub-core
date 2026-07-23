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

package it.smartcommunitylabdhub.metrics.local;

import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.metrics.ResourceMetrics;
import it.smartcommunitylabdhub.metrics.ResourceMetricsStore;
import it.smartcommunitylabdhub.metrics.persistence.ResourceMetricsDTOBuilder;
import it.smartcommunitylabdhub.metrics.persistence.ResourceMetricsEntity;
import it.smartcommunitylabdhub.metrics.persistence.ResourceMetricsEntityBuilder;
import it.smartcommunitylabdhub.metrics.persistence.ResourceMetricsRepository;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Slf4j
public class LocalResourceMetricsStore implements ResourceMetricsStore {

    private ResourceMetricsRepository repository;
    private ResourceMetricsEntityBuilder entityBuilder;
    private ResourceMetricsDTOBuilder dtoBuilder;

    private StringKeyGenerator keyGenerator = () -> UUID.randomUUID().toString().replace("-", "");

    public LocalResourceMetricsStore(
        ResourceMetricsRepository repository,
        ResourceMetricsEntityBuilder entityBuilder,
        ResourceMetricsDTOBuilder dtoBuilder
    ) {
        Assert.notNull(repository, "repository is required");
        Assert.notNull(entityBuilder, "entityBuilder is required");
        Assert.notNull(dtoBuilder, "dtoBuilder is required");

        this.repository = repository;
        this.entityBuilder = entityBuilder;
        this.dtoBuilder = dtoBuilder;
    }

    @Autowired(required = false)
    public void setKeyGenerator(StringKeyGenerator keyGenerator) {
        Assert.notNull(keyGenerator, "key generator can not be null");
        this.keyGenerator = keyGenerator;
    }

    @Override
    @Nullable
    public ResourceMetrics findResourceMetrics(@NotNull String id) throws SystemException {
        log.debug("find resource metrics with id {}", id);
        ResourceMetricsEntity entity = repository.findById(id).orElse(null);
        if (entity == null) {
            return null;
        }

        return dtoBuilder.convert(entity);
    }

    @Override
    public ResourceMetrics createResourceMetrics(@NotNull ResourceMetrics resourceMetrics)
        throws DuplicatedEntityException, BindException, IllegalArgumentException, SystemException {
        //id could be null
        String id = resourceMetrics.getId();
        if (!StringUtils.hasText(id)) {
            id = keyGenerator.generateKey();
        }

        log.debug("create resource metrics with id {}", resourceMetrics.getId());
        ResourceMetricsEntity entity = entityBuilder.convert(resourceMetrics);
        entity = repository.saveAndFlush(entity);
        return dtoBuilder.convert(entity);
    }

    @Override
    public ResourceMetrics updateResourceMetrics(@NotNull String id, @NotNull ResourceMetrics resourceMetrics)
        throws NoSuchEntityException, BindException, IllegalArgumentException, SystemException {
        log.debug("update resource metrics with id {}", id);
        ResourceMetricsEntity entity = repository.findById(id).orElse(null);
        if (entity == null) {
            throw new NoSuchEntityException("ResourceMetrics not found with id " + id);
        }

        //update only metrics and metadata, keep project and created/updated
        ResourceMetricsEntity e = entityBuilder.convert(resourceMetrics);
        entity.setMetadata(e.getMetadata());
        entity.setData(e.getData());

        entity = repository.saveAndFlush(entity);
        return dtoBuilder.convert(entity);
    }

    @Override
    public void deleteResourceMetrics(@NotNull String id) throws SystemException {
        log.debug("delete resource metrics with id {}", id);
        ResourceMetricsEntity entity = repository.findById(id).orElse(null);
        if (entity == null) {
            return;
        }

        repository.delete(entity);
    }

    @Override
    public List<ResourceMetrics> findResourceMetrics() throws SystemException {
        log.debug("find all resource metrics");
        List<ResourceMetricsEntity> entities = repository.findAll();
        if (entities.isEmpty()) {
            return List.of();
        }

        return entities.stream().map(dtoBuilder::convert).toList();
    }

    @Override
    public List<ResourceMetrics> findResourceMetricsByProject(@NotNull String project) throws SystemException {
        log.debug("find resource metrics for project {}", project);
        List<ResourceMetricsEntity> entities = repository.findByProject(project);
        if (entities.isEmpty()) {
            return List.of();
        }

        return entities.stream().map(dtoBuilder::convert).toList();
    }

    @Override
    public List<ResourceMetrics> findResourceMetricsByRun(@NotNull String runId) throws SystemException {
        log.debug("find resource metrics for run {}", runId);
        List<ResourceMetricsEntity> entities = repository.findByRun(runId);
        if (entities.isEmpty()) {
            return List.of();
        }

        return entities.stream().map(dtoBuilder::convert).toList();
    }

    @Override
    public List<ResourceMetrics> findResourceMetricsByUser(@NotNull String user) throws SystemException {
        log.debug("find resource metrics for user {}", user);
        List<ResourceMetricsEntity> entities = repository.findByCreatedBy(user);
        if (entities.isEmpty()) {
            return List.of();
        }

        return entities.stream().map(dtoBuilder::convert).toList();
    }

    @Override
    public void deleteResourceMetricsByProject(@NotNull String project) throws SystemException {
        log.debug("delete resource metrics for project {}", project);
        List<ResourceMetricsEntity> entities = repository.findByProject(project);
        if (entities.isEmpty()) {
            return;
        }

        repository.deleteAllInBatch(entities);
    }

    @Override
    public void deleteResourceMetricsByUser(@NotNull String user) throws SystemException {
        log.debug("delete resource metrics for user {}", user);
        List<ResourceMetricsEntity> entities = repository.findByCreatedBy(user);
        if (entities.isEmpty()) {
            return;
        }

        repository.deleteAllInBatch(entities);
    }

    @Override
    public void deleteResourceMetricsByRun(@NotNull String runId) throws SystemException {
        log.debug("delete resource metrics for run {}", runId);
        List<ResourceMetricsEntity> entities = repository.findByRun(runId);
        if (entities.isEmpty()) {
            return;
        }

        repository.deleteAllInBatch(entities);
    }

    @Override
    public void deleteResourceMetrics() throws SystemException {
        log.debug("delete all resource metrics");
        List<ResourceMetricsEntity> entities = repository.findAll();
        if (entities.isEmpty()) {
            return;
        }

        repository.deleteAllInBatch(entities);
    }
}
