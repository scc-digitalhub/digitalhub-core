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

package it.smartcommunitylabdhub.core.runs.service;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.metrics.Metrics;
import it.smartcommunitylabdhub.commons.models.metrics.NumberOrNumberArray;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.services.LogService;
import it.smartcommunitylabdhub.commons.services.MetricsService;
import it.smartcommunitylabdhub.commons.services.SpecRegistry;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.core.components.infrastructure.specs.SpecValidator;
import it.smartcommunitylabdhub.core.metrics.MetricsManager;
import it.smartcommunitylabdhub.core.persistence.AbstractEntity_;
import it.smartcommunitylabdhub.core.projects.persistence.ProjectEntity;
import it.smartcommunitylabdhub.core.queries.specifications.CommonSpecification;
import it.smartcommunitylabdhub.core.runs.persistence.RunEntity;
import it.smartcommunitylabdhub.core.runs.persistence.RunEntityBuilder;
import it.smartcommunitylabdhub.core.services.EntityService;
import it.smartcommunitylabdhub.core.tasks.persistence.TaskEntity;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
public class RunServiceImpl implements SearchableRunService, MetricsService<Run> {

    @Autowired
    private EntityService<Run, RunEntity> entityService;

    @Autowired
    private RunEntityBuilder entityBuilder;

    @Autowired
    private EntityService<Task, TaskEntity> taskEntityService;

    @Autowired
    private LogService logService;

    @Autowired
    private EntityService<Project, ProjectEntity> projectService;

    @Autowired
    private SpecRegistry specRegistry;

    @Autowired
    private SpecValidator validator;

    @Autowired
    private MetricsManager metricsManager;

    @Override
    public Page<Run> listRuns(Pageable pageable) {
        log.debug("list runs page {}", pageable);
        try {
            return entityService.list(pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Run> listRunsByUser(@NotNull String user) {
        log.debug("list all runs for user {}  ", user);
        try {
            return entityService.searchAll(CommonSpecification.createdByEquals(user));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Run> listRunsByProject(@NotNull String project) {
        log.debug("list all runs for project {}  ", project);
        try {
            return entityService.searchAll(CommonSpecification.projectEquals(project));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Run> searchRuns(Pageable pageable, @Nullable SearchFilter<RunEntity> filter) {
        log.debug("list runs page {}, filter {}", pageable, String.valueOf(filter));
        try {
            Specification<RunEntity> specification = filter != null ? filter.toSpecification() : null;
            if (specification != null) {
                return entityService.search(specification, pageable);
            } else {
                return entityService.list(pageable);
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Run> listRunsByProject(@NotNull String project, Pageable pageable) {
        log.debug("list runs for project {} page {}", project, pageable);
        Specification<RunEntity> specification = Specification.allOf(CommonSpecification.projectEquals(project));
        try {
            return entityService.search(specification, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Run> searchRunsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<RunEntity> filter
    ) {
        log.debug("list runs for project {} with {} page {}", project, String.valueOf(filter), pageable);
        Specification<RunEntity> filterSpecification = filter != null ? filter.toSpecification() : null;
        Specification<RunEntity> specification = Specification.allOf(
            CommonSpecification.projectEquals(project),
            filterSpecification
        );
        try {
            return entityService.search(specification, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Run> getRunsByTaskId(@NotNull String taskId) {
        log.debug("list runs for task {}", taskId);
        try {
            Task task = taskEntityService.find(taskId);
            if (task == null) {
                return Collections.emptyList();
            }

            //define a spec for runs building task path
            String path = (task.getKind() + "://" + task.getProject() + "/" + task.getId());
            Specification<RunEntity> where = Specification.allOf(
                CommonSpecification.projectEquals(task.getProject()),
                createTaskSpecification(path)
            );

            //fetch all runs ordered by created DESC
            Specification<RunEntity> specification = (root, query, builder) -> {
                query.orderBy(builder.desc(root.get(AbstractEntity_.CREATED)));
                return where.toPredicate(root, query, builder);
            };

            return entityService.searchAll(specification);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Run findRun(@NotNull String id) {
        log.debug("find run with id {}", String.valueOf(id));
        try {
            return entityService.find(id);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Run getRun(@NotNull String id) throws NoSuchEntityException {
        log.debug("get run with id {}", String.valueOf(id));

        try {
            return entityService.get(id);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.RUN.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Run createRun(@NotNull Run dto) throws DuplicatedEntityException, BindException, IllegalArgumentException {
        log.debug("create run");
        if (log.isTraceEnabled()) {
            log.trace("dto: {}", dto);
        }
        try {
            //validate project
            String projectId = dto.getProject();
            if (!StringUtils.hasText(projectId) || projectService.find(projectId) == null) {
                throw new IllegalArgumentException("invalid or missing project");
            }
            //TODO check if project exists?

            //check base run spec
            RunBaseSpec runSpec = new RunBaseSpec();
            runSpec.configure(dto.getSpec());

            // Parse and export Spec
            Spec spec = specRegistry.createSpec(dto.getKind(), dto.getSpec());
            if (spec == null) {
                throw new IllegalArgumentException("invalid kind");
            }

            //validate
            validator.validateSpec(spec);

            //update spec as exported
            dto.setSpec(spec.toMap());

            String taskPath = runSpec.getTask();
            if (!StringUtils.hasText(taskPath)) {
                throw new IllegalArgumentException("missing task");
            }

            RunSpecAccessor runSpecAccessor = RunSpecAccessor.with(dto.getSpec());
            if (!StringUtils.hasText(runSpecAccessor.getProject())) {
                throw new IllegalArgumentException("spec: missing project");
            }

            //check project match
            if (!projectId.equals(runSpecAccessor.getProject())) {
                throw new IllegalArgumentException("project mismatch");
            }
            if (!StringUtils.hasText(runSpecAccessor.getTask())) {
                throw new IllegalArgumentException("spec: missing task");
            }
            if (!StringUtils.hasText(runSpecAccessor.getTaskId())) {
                throw new IllegalArgumentException("spec: missing task id");
            }

            //check if task exists and matches
            Task task = taskEntityService.find(runSpecAccessor.getTaskId());
            if (task == null) {
                throw new IllegalArgumentException("invalid task");
            }
            if (!projectId.equals(task.getProject())) {
                throw new IllegalArgumentException("project mismatch");
            }

            //TODO check if run kind matches allowed for task/runtime

            try {
                // store the run in db
                return entityService.create(dto);
            } catch (DuplicatedEntityException e) {
                throw new DuplicatedEntityException(EntityName.RUN.toString(), dto.getId());
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Run updateRun(@NotNull String id, @NotNull Run dto)
        throws NoSuchEntityException, BindException, IllegalArgumentException {
        log.debug("update run with id {}", String.valueOf(id));
        try {
            //fetch current and merge
            Run current = entityService.get(id);

            //spec is not modifiable *after* build
            StatusFieldAccessor status = StatusFieldAccessor.with(current.getStatus());
            if (State.CREATED.name().equals(status.getState())) {
                //we accept updates, parse and export Spec
                Spec spec = specRegistry.createSpec(dto.getKind(), dto.getSpec());
                if (spec == null) {
                    throw new IllegalArgumentException("invalid kind");
                }

                //validate
                validator.validateSpec(spec);

                //update spec as exported
                dto.setSpec(spec.toMap());
            } else {
                //spec is sealed, enforce
                dto.setSpec(current.getSpec());
            }

            //state is modifiable *only* for local runs
            RunSpecAccessor specAccessor = RunSpecAccessor.with(current.getSpec());
            if (!specAccessor.isLocalExecution()) {
                //keep base status from current
                RunBaseStatus bs = RunBaseStatus.with(current.getSpec());
                dto.setStatus(MapUtils.mergeMultipleMaps(dto.getStatus(), bs.toMap()));
            }

            //TODO: implement logic to update status only in some states

            //update, run is modifiable
            return entityService.update(id, dto);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.RUN.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteRun(@NotNull String id, @Nullable Boolean cascade) {
        log.debug("delete run with id {}", String.valueOf(id));
        try {
            Run run = findRun(id);
            if (run != null) {
                if (Boolean.TRUE.equals(cascade)) {
                    log.debug("cascade delete logs for run with id {}", String.valueOf(id));
                    logService.deleteLogsByRunId(id);
                }

                //delete the run
                entityService.delete(id);
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteRunsByTaskId(@NotNull String taskId) {
        log.debug("delete runs for task {}", taskId);

        getRunsByTaskId(taskId).forEach(run -> deleteRun(run.getId(), Boolean.TRUE));
    }

    private Specification<RunEntity> createTaskSpecification(String task) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("task"), task);
    }

    @Override
    public Map<String, NumberOrNumberArray> getMetrics(@NotNull String entityId)
        throws StoreException, SystemException {
        try {
            Run entity = entityService.get(entityId);
            StatusFieldAccessor statusFieldAccessor = StatusFieldAccessor.with(entity.getStatus());
            Map<String, NumberOrNumberArray> metrics = statusFieldAccessor.getMetrics();
            if (metrics != null) {
                Map<String, NumberOrNumberArray> entityMetrics = metricsManager.getMetrics(
                    EntityName.RUN.getValue(),
                    entityId
                );
                for (Map.Entry<String, NumberOrNumberArray> entry : entityMetrics.entrySet()) {
                    if (metrics.containsKey(entry.getKey())) continue;
                    metrics.put(entry.getKey(), entry.getValue());
                }
                return metrics;
            }
            return metricsManager.getMetrics(EntityName.RUN.getValue(), entityId);
        } catch (Exception e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public NumberOrNumberArray getMetrics(@NotNull String entityId, @NotNull String name)
        throws StoreException, SystemException {
        try {
            Run entity = entityService.get(entityId);
            StatusFieldAccessor statusFieldAccessor = StatusFieldAccessor.with(entity.getStatus());
            Map<String, NumberOrNumberArray> metrics = statusFieldAccessor.getMetrics();
            if ((metrics != null) && metrics.containsKey(name)) return metrics.get(name);
            return metricsManager.getMetrics(EntityName.RUN.getValue(), entityId, name);
        } catch (Exception e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Metrics saveMetrics(@NotNull String entityId, @NotNull String name, NumberOrNumberArray data)
        throws StoreException, SystemException {
        return metricsManager.saveMetrics(EntityName.RUN.getValue(), entityId, name, data);
    }
}
