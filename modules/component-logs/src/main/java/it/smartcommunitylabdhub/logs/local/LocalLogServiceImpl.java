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

package it.smartcommunitylabdhub.logs.local;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.logs.Log;
import it.smartcommunitylabdhub.logs.LogService;
import it.smartcommunitylabdhub.logs.local.persistence.LogEntity;
import it.smartcommunitylabdhub.logs.local.persistence.LogRepository;
import it.smartcommunitylabdhub.projects.Project;
import it.smartcommunitylabdhub.runs.Run;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

@Transactional
@Slf4j
public class LocalLogServiceImpl implements LogService {

    public static final int MAX_LENGTH = 2 * 1024 * 1024; //2MB

    @Value("${logs.max-length}")
    private int maxLength = MAX_LENGTH;

    private LogRepository logRepository;
    private Converter<LogEntity, Log> entityConverter;
    private Converter<SearchFilter<Log>, SearchFilter<LogEntity>> filterConverter;

    private EntityRepository<Run> runEntityService;

    public LocalLogServiceImpl(LogRepository logRepository, Converter<LogEntity, Log> entityConverter) {
        Assert.notNull(logRepository, "log repository can not be null");
        Assert.notNull(entityConverter, "entity converter can not be null");

        this.logRepository = logRepository;
        this.entityConverter = entityConverter;
    }

    @Autowired
    public void setRunEntityService(EntityRepository<Run> runEntityService) {
        this.runEntityService = runEntityService;
    }

    @Autowired(required = false)
    public void setFilterConverter(Converter<SearchFilter<Log>, SearchFilter<LogEntity>> filterConverter) {
        this.filterConverter = filterConverter;
    }

    public Page<Log> listLogs(@NonNull Pageable pageable) {
        log.debug("list logs page {}", pageable);
        return logRepository.findAll(pageable).map(entityConverter::convert);
    }

    @Override
    public Page<Log> searchLogs(@NonNull Pageable pageable, @Nullable SearchFilter<Log> filter) {
        log.debug("list logs page {}, filter {}", pageable, String.valueOf(filter));
        if (filter != null && filterConverter != null) {
            SearchFilter<LogEntity> ef = filterConverter.convert(filter);
            if (ef == null) {
                log.error("invalid filter {}", String.valueOf(filter));
                throw new IllegalArgumentException("invalid filter");
            }

            return logRepository.findAll(ef.toSpecification(), pageable).map(entityConverter::convert);
        } else {
            return logRepository.findAll(pageable).map(entityConverter::convert);
        }
    }

    public List<Log> listLogsByUser(@NotNull String user) {
        log.debug("list all logs for user {}  ", user);
        return logRepository.findByCreatedBy(user).stream().map(entityConverter::convert).toList();
    }

    @Override
    public List<Log> listLogsByProject(@NotNull String project) {
        log.debug("list all logs for project {}  ", project);
        return logRepository.findByProject(project).stream().map(entityConverter::convert).toList();
    }

    @Override
    public Page<Log> listLogsByProject(@NotNull String project, @NonNull Pageable pageable) {
        log.debug("list logs for project {} page {}", project, pageable);

        return logRepository.findByProject(project, pageable).map(entityConverter::convert);
    }

    @Override
    public Page<Log> searchLogsByProject(
        @NotNull String project,
        @NonNull Pageable pageable,
        @Nullable SearchFilter<Log> filter
    ) {
        log.debug("list logs for project {} with {} page {}", project, String.valueOf(filter), pageable);

        if (filter != null && filterConverter != null) {
            SearchFilter<LogEntity> ef = filterConverter.convert(filter);
            if (ef == null) {
                log.error("invalid filter {}", String.valueOf(filter));
                throw new IllegalArgumentException("invalid filter");
            }

            Specification<LogEntity> where = Specification.allOf(
                createProjectSpecification(project),
                ef.toSpecification()
            );

            //fetch all logs ordered by date ASC
            Specification<LogEntity> specification = (root, query, builder) -> {
                query.orderBy(builder.asc(root.get("created")));
                return where.toPredicate(root, query, builder);
            };

            return logRepository.findAll(specification, pageable).map(entityConverter::convert);
        } else {
            return logRepository.findByProject(project, pageable).map(entityConverter::convert);
        }
    }

    @Override
    public List<Log> getLogsByRunId(@NotNull String runId) {
        log.debug("list logs for run {}", runId);
        try {
            Run run = runEntityService.find(runId);
            if (run == null) {
                return Collections.emptyList();
            }

            //define a spec for logs building run path
            Specification<LogEntity> where = Specification.allOf(
                createProjectSpecification(run.getProject()),
                createRunSpecification(runId)
            );

            //fetch all logs ordered by date ASC
            Specification<LogEntity> specification = (root, query, builder) -> {
                query.orderBy(builder.asc(root.get("created")));
                return where.toPredicate(root, query, builder);
            };

            return logRepository.findAll(specification).stream().map(entityConverter::convert).toList();
        } catch (StoreException e) {
            log.error("error fetching logs for run {}", runId, e);
            throw new SystemException(e.getMessage());
        }
    }

    private Specification<LogEntity> createRunSpecification(String run) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.equal(root.get("run"), run);
        };
    }

    private Specification<LogEntity> createProjectSpecification(String project) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.equal(root.get("project"), project);
        };
    }
}
