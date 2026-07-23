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

import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.logs.Log;
import it.smartcommunitylabdhub.logs.LogService;
import it.smartcommunitylabdhub.logs.LogStore;
import it.smartcommunitylabdhub.logs.local.persistence.LogEntity;
import it.smartcommunitylabdhub.logs.local.persistence.LogRepository;
import it.smartcommunitylabdhub.runs.Run;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;

@Transactional
@Slf4j
public class LocalLogServiceImpl implements LogService, LogStore {

    public static final int MAX_LENGTH = 2 * 1024 * 1024; //2MB

    @Value("${logs.max-length}")
    private int maxLength = MAX_LENGTH;

    private LogRepository logRepository;
    private Converter<LogEntity, Log> entityConverter;
    private Converter<Log, LogEntity> dtoConverter;
    private Converter<SearchFilter<Log>, SearchFilter<LogEntity>> filterConverter;

    private StringKeyGenerator keyGenerator = () -> UUID.randomUUID().toString().replace("-", "");

    @Autowired
    private EntityRepository<Run> runEntityService;

    @Autowired
    private EntityRepository<Project> projectService;

    public LocalLogServiceImpl(
        LogRepository logRepository,
        Converter<LogEntity, Log> entityConverter,
        Converter<Log, LogEntity> dtoConverter
    ) {
        Assert.notNull(logRepository, "log repository can not be null");
        Assert.notNull(entityConverter, "entity converter can not be null");
        Assert.notNull(dtoConverter, "dto converter can not be null");

        this.logRepository = logRepository;
        this.entityConverter = entityConverter;
        this.dtoConverter = dtoConverter;
    }

    @Autowired(required = false)
    public void setFilterConverter(Converter<SearchFilter<Log>, SearchFilter<LogEntity>> filterConverter) {
        this.filterConverter = filterConverter;
    }

    @Autowired(required = false)
    public void setKeyGenerator(StringKeyGenerator keyGenerator) {
        Assert.notNull(keyGenerator, "key generator can not be null");
        this.keyGenerator = keyGenerator;
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

    @Override
    public Log findLog(@NotNull String id) {
        log.debug("find log with id {}", String.valueOf(id));
        return logRepository.findById(id).map(entityConverter::convert).orElse(null);
    }

    @Override
    public Log getLog(@NotNull String id) throws NoSuchEntityException {
        log.debug("get log with id {}", String.valueOf(id));

        return logRepository
            .findById(id)
            .map(entityConverter::convert)
            .orElseThrow(() -> new NoSuchEntityException("log not found with id " + String.valueOf(id)));
    }

    @Override
    public Log createLog(@NotNull Log dto) throws DuplicatedEntityException, BindException, IllegalArgumentException {
        log.debug("create log");
        try {
            //validate project
            String projectId = dto.getProject();
            if (!StringUtils.hasText(projectId) || projectService.find(projectId) == null) {
                throw new IllegalArgumentException("invalid or missing project");
            }

            String runId = dto.getRun();
            if (!StringUtils.hasText(runId)) {
                throw new IllegalArgumentException("missing or invalid run");
            }

            Run run = runEntityService.find(runId);
            if (run == null) {
                throw new IllegalArgumentException("missing or invalid run");
            }

            if (!projectId.equals(run.getProject())) {
                throw new IllegalArgumentException("project mismatch");
            }

            //check if too big and slice
            if (dto.getContent() != null && dto.getContent().length() > maxLength) {
                log.debug("log content too long, slice to {}", maxLength);
                dto.setContent(dto.getContent().substring(dto.getContent().length() - maxLength));
            }

            //create as new
            LogEntity e = dtoConverter.convert(dto);
            if (e == null) {
                throw new IllegalArgumentException("invalid log entry");
            }

            if (e.getId() != null && logRepository.existsById(e.getId())) {
                throw new DuplicatedEntityException("log already exists with id " + e.getId());
            }
            if (e.getId() == null) {
                e.setId(keyGenerator.generateKey());
            }

            e = logRepository.saveAndFlush(e);
            Log d = entityConverter.convert(e);
            if (log.isTraceEnabled()) {
                log.trace("log: {}", d);
            }

            return d;
        } catch (StoreException e) {
            log.error("error creating log", e);
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Log updateLog(@NotNull String id, @NotNull Log dto)
        throws NoSuchEntityException, BindException, IllegalArgumentException {
        log.debug("update log with id {}", String.valueOf(id));
        //fetch current and merge
        LogEntity current = logRepository
            .findById(id)
            .orElseThrow(() -> new NoSuchEntityException("log not found with id " + String.valueOf(id)));

        //hardcoded: run ref is not modifiable
        if (!current.getRun().equals(dto.getRun())) {
            throw new IllegalArgumentException("run reference can not be modified");
        }

        //check if too big and slice
        if (dto.getContent() != null && dto.getContent().length() > maxLength) {
            log.debug("log content too long, slice to {}", maxLength);
            dto.setContent(dto.getContent().substring(dto.getContent().length() - maxLength));
        }

        //full update, log is modifiable
        LogEntity e = dtoConverter.convert(dto);
        current.setContent(e.getContent());
        current = logRepository.saveAndFlush(current);
        return entityConverter.convert(current);
    }

    @Override
    public void deleteLog(@NotNull String id) {
        log.debug("delete log with id {}", String.valueOf(id));
        logRepository.deleteById(id);
    }

    @Override
    public void deleteLogsByRunId(@NotNull String runId) {
        log.debug("delete logs for run {}", runId);
        List<LogEntity> logs = logRepository.findByRun(runId);
        if (!logs.isEmpty()) {
            logRepository.deleteAll(logs);
        }
    }

    @Override
    public void deleteLogsByProject(@NotNull String project) {
        log.debug("delete logs for project {}", project);
        List<LogEntity> logs = logRepository.findByProject(project);
        if (!logs.isEmpty()) {
            logRepository.deleteAll(logs);
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
