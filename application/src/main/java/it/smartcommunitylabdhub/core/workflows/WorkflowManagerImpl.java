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

package it.smartcommunitylabdhub.core.workflows;

import it.smartcommunitylabdhub.commons.Fields;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.models.workflow.Workflow;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.commons.services.VersionableEntityService;
import it.smartcommunitylabdhub.commons.services.WorkflowManager;
import it.smartcommunitylabdhub.core.persistence.AbstractEntity_;
import it.smartcommunitylabdhub.core.queries.specifications.CommonSpecification;
import it.smartcommunitylabdhub.core.repositories.SearchableEntityRepository;
import it.smartcommunitylabdhub.core.tasks.persistence.TaskEntity;
import it.smartcommunitylabdhub.core.workflows.persistence.WorkflowEntity;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindException;

@Service
@Transactional
@Slf4j
public class WorkflowManagerImpl implements WorkflowManager {

    @Autowired
    private EntityService<Workflow> entityService;

    @Autowired
    private VersionableEntityService<Workflow> versionableService;

    @Autowired
    private SearchableEntityRepository<WorkflowEntity, Workflow> entityRepository;

    @Autowired
    private SearchableEntityRepository<TaskEntity, Task> taskEntityService;

    @Override
    public Page<Workflow> listWorkflows(Pageable pageable) {
        log.debug("list workflows page {}", pageable);
        try {
            return entityService.list(pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Workflow> listLatestWorkflows() {
        log.debug("list latest workflows");

        try {
            return versionableService.listLatest();
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Workflow> listLatestWorkflows(Pageable pageable) {
        log.debug("list latest workflows page {}", pageable);

        try {
            return versionableService.listLatest(pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Workflow> listWorkflowsByUser(@NotNull String user) {
        log.debug("list all workflows for user {}  ", user);

        try {
            return entityService.listByUser(user);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Workflow> searchWorkflows(Pageable pageable, @Nullable SearchFilter<Workflow> filter) {
        log.debug("search workflows page {}, filter {}", pageable, String.valueOf(filter));

        try {
            return entityService.search(filter, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Workflow> searchLatestWorkflows(Pageable pageable, @Nullable SearchFilter<Workflow> filter) {
        log.debug("search latest workflows with {} page {}", String.valueOf(filter), pageable);

        try {
            return versionableService.searchLatest(filter, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Workflow> listWorkflowsByProject(@NotNull String project) {
        log.debug("list all workflows for project {}", project);

        try {
            return entityService.listByProject(project);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Workflow> listWorkflowsByProject(@NotNull String project, Pageable pageable) {
        log.debug("list all workflows for project {}  page {}", project, pageable);

        try {
            return entityService.listByProject(project, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Workflow> listLatestWorkflowsByProject(@NotNull String project) {
        log.debug("list latest workflows for project {}", project);

        try {
            return versionableService.listLatestByProject(project);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Workflow> listLatestWorkflowsByProject(@NotNull String project, Pageable pageable) {
        log.debug("list latest workflows for project {}  page {}", project, pageable);

        try {
            return versionableService.listLatestByProject(project, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Workflow> searchWorkflowsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<Workflow> filter
    ) {
        log.debug("search all workflows for project {} with {} page {}", project, String.valueOf(filter), pageable);

        try {
            return entityService.searchByProject(project, filter, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Workflow> searchLatestWorkflowsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<Workflow> filter
    ) {
        log.debug("search latest workflows for project {} with {} page {}", project, String.valueOf(filter), pageable);

        try {
            return versionableService.searchLatestByProject(project, filter, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Workflow> findWorkflows(@NotNull String project, @NotNull String name) {
        log.debug("find workflows for project {} with name {}", project, name);

        try {
            return versionableService.findAll(project, name);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Workflow> findWorkflows(@NotNull String project, @NotNull String name, Pageable pageable) {
        log.debug("find workflows for project {} with name {} page {}", project, name, pageable);

        try {
            return versionableService.findAll(project, name, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Workflow findWorkflow(@NotNull String id) {
        log.debug("find workflow with id {}", String.valueOf(id));
        try {
            return entityService.find(id);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Workflow getWorkflow(@NotNull String id) throws NoSuchEntityException {
        log.debug("get workflow with id {}", String.valueOf(id));

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
    public Workflow getLatestWorkflow(@NotNull String project, @NotNull String name) throws NoSuchEntityException {
        log.debug("get latest workflow for project {} with name {}", project, name);
        try {
            return versionableService.getLatest(project, name);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Workflow createWorkflow(@NotNull Workflow dto)
        throws DuplicatedEntityException, BindException, IllegalArgumentException {
        log.debug("create workflow");
        if (log.isTraceEnabled()) {
            log.trace("dto: {}", dto);
        }
        try {
            return entityService.create(dto);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Workflow updateWorkflow(@NotNull String id, @NotNull Workflow dto)
        throws NoSuchEntityException, BindException, IllegalArgumentException {
        log.debug("update workflow with id {}", String.valueOf(id));
        try {
            return entityService.update(id, dto);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.WORKFLOW.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Deprecated(forRemoval = true)
    @Override
    public Workflow updateWorkflow(@NotNull String id, @NotNull Workflow dto, boolean force)
        throws NoSuchEntityException {
        log.debug("force update workflow with id {}", String.valueOf(id));
        try {
            //force update
            //no validation
            return entityRepository.update(id, dto);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.WORKFLOW.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteWorkflow(@NotNull String id, @Nullable Boolean cascade) {
        log.debug("delete workflow with id {}", String.valueOf(id));
        try {
            entityService.delete(id, cascade);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteWorkflows(@NotNull String project, @NotNull String name) {
        log.debug("delete workflows for project {} with name {}", project, name);
        try {
            versionableService.deleteAll(project, name, true);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteWorkflowsByProject(@NotNull String project) {
        log.debug("delete workflows for project {}", project);
        try {
            entityService.deleteByProject(project, true);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Task> getTasksByWorkflowId(@NotNull String workflowId) throws SystemException {
        log.debug("list tasks for workflow {}", workflowId);
        try {
            Workflow workflow = entityService.find(workflowId);
            if (workflow == null) {
                return Collections.emptyList();
            }

            //define a spec for tasks building workflow path
            String path =
                (workflow.getKind() +
                    "://" +
                    workflow.getProject() +
                    "/" +
                    workflow.getName() +
                    ":" +
                    workflow.getId());

            Specification<TaskEntity> where = Specification.allOf(
                CommonSpecification.projectEquals(workflow.getProject()),
                createWorkflowSpecification(path)
            );

            //fetch all tasks ordered by kind ASC
            Specification<TaskEntity> specification = (root, query, builder) -> {
                query.orderBy(builder.asc(root.get(AbstractEntity_.KIND)));
                return where.toPredicate(root, query, builder);
            };

            return taskEntityService.searchAll(specification);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    private Specification<TaskEntity> createWorkflowSpecification(String workflow) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.equal(root.get(Fields.WORKFLOW), workflow);
        };
    }
}
