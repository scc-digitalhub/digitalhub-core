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

package it.smartcommunitylabdhub.core.logs;

import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.log.Log;
import it.smartcommunitylabdhub.commons.models.log.LogBaseSpec;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.commons.services.LogService;
import it.smartcommunitylabdhub.core.logs.persistence.LogEntity;
import it.smartcommunitylabdhub.core.persistence.AbstractEntity_;
import it.smartcommunitylabdhub.core.queries.specifications.CommonSpecification;
import it.smartcommunitylabdhub.core.repositories.SearchableEntityRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;

@Service
@Transactional
@Slf4j
public class LogServiceImpl implements LogService {

    public static final int MAX_LENGTH = 2 * 1024 * 1024; //2MB

    @Value("${kubernetes.logs.max-length}")
    private int maxLength = MAX_LENGTH;

    @Autowired
    private EntityService<Log> entityService;

    @Autowired
    private SearchableEntityRepository<LogEntity, Log> entityRepository;

    @Autowired
    private EntityRepository<Run> runEntityService;

    @Autowired
    private EntityRepository<Project> projectService;

    @Override
    public Page<Log> listLogs(Pageable pageable) {
        log.debug("list logs page {}", pageable);
        try {
            return entityService.list(pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Log> searchLogs(Pageable pageable, @Nullable SearchFilter<Log> filter) {
        log.debug("list logs page {}, filter {}", pageable, String.valueOf(filter));
        try {
            return entityService.search(filter, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Log> listLogsByUser(@NotNull String user) {
        log.debug("list all logs for user {}  ", user);
        try {
            return entityService.listByUser(user);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Log> listLogsByProject(@NotNull String project) {
        log.debug("list all logs for project {}  ", project);
        try {
            return entityService.listByProject(project);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Log> listLogsByProject(@NotNull String project, Pageable pageable) {
        log.debug("list logs for project {} page {}", project, pageable);
        try {
            return entityService.listByProject(project, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Log> searchLogsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<Log> filter
    ) {
        log.debug("list logs for project {} with {} page {}", project, String.valueOf(filter), pageable);

        try {
            return entityService.searchByProject(project, filter, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
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
                CommonSpecification.projectEquals(run.getProject()),
                createRunSpecification(runId)
            );

            //fetch all logs ordered by date ASC
            Specification<LogEntity> specification = (root, query, builder) -> {
                query.orderBy(builder.asc(root.get(AbstractEntity_.CREATED)));
                return where.toPredicate(root, query, builder);
            };

            return entityRepository.searchAll(specification).stream().collect(Collectors.toList());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Log findLog(@NotNull String id) {
        log.debug("find log with id {}", String.valueOf(id));
        try {
            return entityService.find(id);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Log getLog(@NotNull String id) throws NoSuchEntityException {
        log.debug("get log with id {}", String.valueOf(id));

        try {
            return entityService.get(id);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.LOG.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
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

            try {
                //parse base spec to resolve run
                LogBaseSpec spec = new LogBaseSpec();
                spec.configure(dto.getSpec());

                String runId = spec.getRun();
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
                return entityService.create(dto);
            } catch (DuplicatedEntityException e) {
                throw new DuplicatedEntityException(EntityName.LOG.toString(), dto.getId());
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Log updateLog(@NotNull String id, @NotNull Log dto)
        throws NoSuchEntityException, BindException, IllegalArgumentException {
        log.debug("update log with id {}", String.valueOf(id));
        try {
            //fetch current and merge
            Log current = entityService.get(id);

            //hardcoded: run ref is not modifiable
            Map<String, Serializable> specMap = new HashMap<>();
            if (dto.getSpec() != null) {
                specMap.putAll(dto.getSpec());
            }
            if (current.getSpec() != null) {
                specMap.put("run", current.getSpec().get("run"));
            }

            //update spec
            dto.setSpec(specMap);

            //check if too big and slice
            if (dto.getContent() != null && dto.getContent().length() > maxLength) {
                log.debug("log content too long, slice to {}", maxLength);
                dto.setContent(dto.getContent().substring(dto.getContent().length() - maxLength));
            }

            //full update, log is modifiable
            return entityRepository.update(id, dto);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.LOG.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteLog(@NotNull String id) {
        log.debug("delete log with id {}", String.valueOf(id));
        try {
            Log log = findLog(id);
            if (log != null) {
                //delete the log
                entityService.delete(id, false);
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteLogsByRunId(@NotNull String runId) {
        log.debug("delete logs for run {}", runId);

        getLogsByRunId(runId).forEach(log -> deleteLog(log.getId()));
    }

    @Override
    public void deleteLogsByProject(@NotNull String project) {
        log.debug("delete logs for project {}", project);
        try {
            entityService.deleteByProject(project, false);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    private Specification<LogEntity> createRunSpecification(String run) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.equal(root.get("run"), run);
        };
    }
}
