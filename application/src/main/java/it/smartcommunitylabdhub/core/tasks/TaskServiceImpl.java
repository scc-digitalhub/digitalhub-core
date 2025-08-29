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

package it.smartcommunitylabdhub.core.tasks;

import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.models.workflow.Workflow;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.commons.services.SpecRegistry;
import it.smartcommunitylabdhub.commons.services.TaskService;
import it.smartcommunitylabdhub.core.components.infrastructure.specs.SpecValidator;
import it.smartcommunitylabdhub.core.queries.specifications.CommonSpecification;
import it.smartcommunitylabdhub.core.repositories.SearchableEntityRepository;
import it.smartcommunitylabdhub.core.runs.persistence.RunEntity;
import it.smartcommunitylabdhub.core.tasks.filters.TaskFilterConverter;
import it.smartcommunitylabdhub.core.tasks.persistence.TaskEntity;
import it.smartcommunitylabdhub.events.EntityAction;
import it.smartcommunitylabdhub.events.EntityOperation;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.convert.converter.Converter;
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
public class TaskServiceImpl implements TaskService {

    @Autowired
    private SearchableEntityRepository<TaskEntity, Task> entityRepository;

    @Autowired
    private EntityRepository<Function> functionService;

    @Autowired
    private EntityRepository<Workflow> workflowService;

    @Autowired
    private EntityRepository<Project> projectService;

    @Autowired
    private SearchableEntityRepository<RunEntity, Run> runService;

    @Autowired
    private SpecRegistry specRegistry;

    @Autowired
    private SpecValidator validator;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private Converter<SearchFilter<Task>, SearchFilter<TaskEntity>> filterConverter = new TaskFilterConverter();

    @Override
    public Page<Task> listTasks(Pageable pageable) {
        log.debug("list tasks page {}", pageable);
        try {
            return entityRepository.list(pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Task> listTasksByUser(@NotNull String user) {
        log.debug("list all tasks for user {}  ", user);
        try {
            return entityRepository.searchAll(CommonSpecification.createdByEquals(user));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Task> listTasksByProject(@NotNull String project) {
        log.debug("list all tasks for project {}  ", project);
        try {
            return entityRepository.searchAll(CommonSpecification.projectEquals(project));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Task> searchTasks(Pageable pageable, @Nullable SearchFilter<Task> filter) {
        log.debug("list tasks page {}, filter {}", pageable, String.valueOf(filter));
        try {
            Specification<TaskEntity> specification = filter != null
                ? filterConverter.convert(filter).toSpecification()
                : null;
            if (specification != null) {
                return entityRepository.search(specification, pageable);
            } else {
                return entityRepository.list(pageable);
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Task> listTasksByProject(@NotNull String project, Pageable pageable) {
        log.debug("list tasks for project {} page {}", project, pageable);
        Specification<TaskEntity> specification = Specification.allOf(CommonSpecification.projectEquals(project));
        try {
            return entityRepository.search(specification, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Task> searchTasksByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<Task> filter
    ) {
        log.debug("list tasks for project {} with {} page {}", project, String.valueOf(filter), pageable);
        Specification<TaskEntity> filterSpecification = filter != null
            ? filterConverter.convert(filter).toSpecification()
            : null;
        Specification<TaskEntity> specification = Specification.allOf(
            CommonSpecification.projectEquals(project),
            filterSpecification
        );
        try {
            return entityRepository.search(specification, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Task findTask(@NotNull String id) {
        log.debug("find task with id {}", String.valueOf(id));
        try {
            return entityRepository.find(id);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Task getTask(@NotNull String id) throws NoSuchEntityException {
        log.debug("get task with id {}", String.valueOf(id));

        try {
            return entityRepository.get(id);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.TASK.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Task createTask(@NotNull Task dto)
        throws DuplicatedEntityException, BindException, IllegalArgumentException {
        log.debug("create task");
        try {
            //validate project
            String projectId = dto.getProject();
            if (!StringUtils.hasText(projectId) || projectService.find(projectId) == null) {
                throw new IllegalArgumentException("invalid or missing project");
            }

            try {
                // Parse and export Spec
                Spec spec = specRegistry.createSpec(dto.getKind(), dto.getSpec());
                if (spec == null) {
                    throw new IllegalArgumentException("invalid kind");
                }

                //validate
                validator.validateSpec(spec);

                //update spec as exported
                dto.setSpec(spec.toMap());

                //check if the same task already exists for the function
                TaskSpecAccessor taskSpecAccessor = TaskSpecAccessor.with(dto.getSpec());
                if (!StringUtils.hasText(taskSpecAccessor.getProject())) {
                    throw new IllegalArgumentException("spec: missing project");
                }
                if (!StringUtils.hasText(taskSpecAccessor.getRuntime())) {
                    throw new IllegalArgumentException("missing runtime");
                }

                //check project match
                if (dto.getProject() != null && !dto.getProject().equals(taskSpecAccessor.getProject())) {
                    throw new IllegalArgumentException("project mismatch");
                }
                dto.setProject(taskSpecAccessor.getProject());

                // task may belong to function or to workflow
                BaseDTO executable = null;
                String function = taskSpecAccessor.getFunction();
                String workflow = taskSpecAccessor.getWorkflow();

                if (StringUtils.hasText(function)) {
                    String functionId = taskSpecAccessor.getFunctionId();
                    executable = functionService.find(functionId);
                }
                if (StringUtils.hasText(workflow)) {
                    String workflowId = taskSpecAccessor.getWorkflowId();
                    executable = workflowService.find(workflowId);
                }

                if (executable == null) {
                    throw new IllegalArgumentException("invalid executable entity");
                }

                //create as new
                return entityRepository.create(dto);
            } catch (DuplicatedEntityException e) {
                throw new DuplicatedEntityException(EntityName.TASK.toString(), dto.getId());
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Task updateTask(@NotNull String id, @NotNull Task dto)
        throws NoSuchEntityException, BindException, IllegalArgumentException {
        log.debug("update task with id {}", String.valueOf(id));
        try {
            //fetch current and merge
            Task current = entityRepository.get(id);

            //hardcoded: function ref is not modifiable
            Map<String, Serializable> specMap = new HashMap<>();
            if (dto.getSpec() != null) {
                specMap.putAll(dto.getSpec());
            }
            if (current.getSpec() != null) {
                specMap.put("function", current.getSpec().get("function"));
            }

            Spec spec = specRegistry.createSpec(dto.getKind(), specMap);
            if (spec == null) {
                throw new IllegalArgumentException("invalid kind");
            }

            //validate
            validator.validateSpec(spec);

            //update spec as exported
            dto.setSpec(spec.toMap());

            //full update, task is modifiable
            return entityRepository.update(id, dto);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.TASK.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteTask(@NotNull String id, @Nullable Boolean cascade) {
        log.debug("delete task with id {}", String.valueOf(id));
        try {
            Task task = findTask(id);
            if (task != null) {
                if (Boolean.TRUE.equals(cascade)) {
                    log.debug("cascade delete runs for task with id {}", String.valueOf(id));

                    //define a spec for runs building task path
                    String path = (task.getKind() + "://" + task.getProject() + "/" + task.getId());
                    Specification<RunEntity> where = Specification.allOf(
                        CommonSpecification.projectEquals(task.getProject()),
                        createTaskSpecification(path)
                    );

                    //delete via async event to let manager do cleanups
                    //TODO do in sync to block for errors
                    runService
                        .searchAll(where)
                        .forEach(run -> {
                            log.debug("publish op: delete for {}", run.getId());
                            EntityOperation<Run> event = new EntityOperation<>(run, EntityAction.DELETE);
                            if (log.isTraceEnabled()) {
                                log.trace("event: {}", String.valueOf(event));
                            }

                            eventPublisher.publishEvent(event);
                        });
                }

                //delete the task
                entityRepository.delete(id);
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    private Specification<RunEntity> createTaskSpecification(String task) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("task"), task);
    }
}
