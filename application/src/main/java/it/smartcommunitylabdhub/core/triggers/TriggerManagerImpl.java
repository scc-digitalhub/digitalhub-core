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

package it.smartcommunitylabdhub.core.triggers;

import it.smartcommunitylabdhub.commons.Fields;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.models.trigger.Trigger;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.commons.services.TriggerManager;
import it.smartcommunitylabdhub.core.persistence.AbstractEntity_;
import it.smartcommunitylabdhub.core.queries.specifications.CommonSpecification;
import it.smartcommunitylabdhub.core.repositories.SearchableEntityRepository;
import it.smartcommunitylabdhub.core.tasks.persistence.TaskEntity;
import it.smartcommunitylabdhub.core.triggers.persistence.TriggerEntity;
import it.smartcommunitylabdhub.triggers.specs.TriggerBaseSpec;
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
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;

@Service
@Transactional
@Slf4j
public class TriggerManagerImpl implements TriggerManager {

    @Autowired
    private EntityService<Trigger> entityService;

    @Autowired
    private SearchableEntityRepository<TriggerEntity, Trigger> entityRepository;

    @Autowired
    private SearchableEntityRepository<TaskEntity, Task> taskEntityService;

    @Override
    public Page<Trigger> listTriggers(Pageable pageable) {
        log.debug("list triggers page {}", pageable);

        try {
            return entityService.list(pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Trigger> listTriggersByUser(@NotNull String user) {
        log.debug("list all triggers for user {}  ", user);

        try {
            return entityService.listByUser(user);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Trigger> listTriggersByProject(@NotNull String project) {
        log.debug("list all triggers for project {}  ", project);

        try {
            return entityService.listByProject(project);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Trigger> listTriggersByProject(@NotNull String project, Pageable pageable) {
        log.debug("list triggers for project {} page {}", project, pageable);

        try {
            return entityService.listByProject(project, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Trigger> listTriggersByTaskId(@NotNull String taskId) {
        log.debug("list triggers for taskId {}", taskId);
        try {
            Task task = taskEntityService.find(taskId);
            if (task == null) {
                return Collections.emptyList();
            }

            //define a spec for triggers building task path
            String path = (task.getKind() + "://" + task.getProject() + "/" + task.getId());

            Specification<TriggerEntity> where = Specification.allOf(
                CommonSpecification.projectEquals(task.getProject()),
                createTaskSpecification(path)
            );

            //fetch all triggers ordered by kind ASC
            Specification<TriggerEntity> specification = (root, query, builder) -> {
                query.orderBy(builder.asc(root.get(AbstractEntity_.KIND)));
                return where.toPredicate(root, query, builder);
            };

            return entityRepository.searchAll(specification);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Trigger> searchTriggers(Pageable pageable, @Nullable SearchFilter<Trigger> filter) {
        log.debug("list triggers page {}, filter {}", pageable, String.valueOf(filter));

        try {
            return entityService.search(filter, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Trigger> searchTriggersByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<Trigger> filter
    ) {
        log.debug("list triggers for project {} with {} page {}", project, String.valueOf(filter), pageable);

        try {
            return entityService.searchByProject(project, filter, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Trigger findTrigger(@NotNull String id) {
        log.debug("find trigger with id {}", String.valueOf(id));
        try {
            return entityService.find(id);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Trigger getTrigger(@NotNull String id) throws NoSuchEntityException {
        log.debug("get trigger with id {}", String.valueOf(id));

        try {
            return entityService.get(id);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.TASK.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Trigger createTrigger(@NotNull Trigger dto)
        throws DuplicatedEntityException, BindException, IllegalArgumentException {
        log.debug("create trigger");
        try {
            //validate project
            String projectId = dto.getProject();
            if (!StringUtils.hasText(projectId)) {
                throw new IllegalArgumentException("invalid or missing project");
            }

            try {
                //check task is valid
                TriggerBaseSpec baseSpec = TriggerBaseSpec.from(dto.getSpec());
                if (!StringUtils.hasText(baseSpec.getTask())) {
                    throw new IllegalArgumentException("spec: missing task");
                }
                if (!StringUtils.hasText(baseSpec.getFunction()) && !StringUtils.hasText(baseSpec.getWorkflow())) {
                    throw new IllegalArgumentException("spec: missing function or workflow");
                }

                //access task details from ref, same as run
                RunSpecAccessor specAccessor = RunSpecAccessor.with(dto.getSpec());

                //check project match
                if (dto.getProject() != null && !dto.getProject().equals(specAccessor.getProject())) {
                    throw new IllegalArgumentException("project mismatch");
                }

                //create as new
                return entityService.create(dto);
            } catch (DuplicatedEntityException e) {
                throw new DuplicatedEntityException(EntityName.TRIGGER.toString(), dto.getId());
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Trigger updateTrigger(@NotNull String id, @NotNull Trigger dto)
        throws NoSuchEntityException, BindException, IllegalArgumentException {
        log.debug("update trigger with id {}", String.valueOf(id));
        try {
            return entityService.update(id, dto);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.TASK.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteTrigger(@NotNull String id, @Nullable Boolean cascade) {
        log.debug("delete trigger with id {}", String.valueOf(id));
        try {
            entityService.delete(id, false);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    private Specification<TriggerEntity> createTaskSpecification(String task) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.equal(root.get(Fields.TASK), task);
        };
    }
}
