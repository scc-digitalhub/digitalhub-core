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

package it.smartcommunitylabdhub.core.functions;

import it.smartcommunitylabdhub.commons.Fields;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.commons.services.FunctionManager;
import it.smartcommunitylabdhub.commons.services.VersionableEntityService;
import it.smartcommunitylabdhub.core.functions.persistence.FunctionEntity;
import it.smartcommunitylabdhub.core.persistence.AbstractEntity_;
import it.smartcommunitylabdhub.core.queries.specifications.CommonSpecification;
import it.smartcommunitylabdhub.core.repositories.SearchableEntityRepository;
import it.smartcommunitylabdhub.core.tasks.persistence.TaskEntity;
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
public class FunctionManagerImpl implements FunctionManager {

    @Autowired
    private EntityService<Function> entityService;

    @Autowired
    private VersionableEntityService<Function> versionableService;

    @Autowired
    private SearchableEntityRepository<FunctionEntity, Function> entityRepository;

    @Autowired
    private SearchableEntityRepository<TaskEntity, Task> taskEntityService;

    @Override
    public Page<Function> listFunctions(Pageable pageable) {
        log.debug("list functions page {}", pageable);
        try {
            return entityService.list(pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Function> listLatestFunctions() {
        log.debug("list latest functions");

        try {
            return versionableService.listLatest();
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Function> listLatestFunctions(Pageable pageable) {
        log.debug("list latest functions page {}", pageable);
        try {
            return versionableService.listLatest(pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Function> listFunctionsByUser(@NotNull String user) {
        log.debug("list all functions for user {}  ", user);
        try {
            return entityService.listByUser(user);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Function> searchFunctions(Pageable pageable, @Nullable SearchFilter<Function> filter) {
        log.debug("list functions page {}, filter {}", pageable, String.valueOf(filter));
        try {
            return entityService.search(filter, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Function> searchLatestFunctions(Pageable pageable, @Nullable SearchFilter<Function> filter) {
        log.debug("search latest functions with {} page {}", String.valueOf(filter), pageable);

        try {
            return versionableService.searchLatest(filter, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Function> listFunctionsByProject(@NotNull String project) {
        log.debug("list functions for project {}", project);

        try {
            return entityService.listByProject(project);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Function> listFunctionsByProject(@NotNull String project, Pageable pageable) {
        log.debug("list functions for project {} page {}", project, pageable);

        try {
            return entityService.listByProject(project, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Function> listLatestFunctionsByProject(@NotNull String project) {
        log.debug("list latest functions for project {}", project);

        try {
            return versionableService.listLatestByProject(project);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Function> listLatestFunctionsByProject(@NotNull String project, Pageable pageable) {
        log.debug("list latest functions for project {} page {}", project, pageable);

        try {
            return versionableService.listLatestByProject(project, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Function> searchLatestFunctionsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<Function> filter
    ) {
        log.debug("search latest functions for project {} with {} page {}", project, String.valueOf(filter), pageable);

        try {
            return versionableService.searchLatestByProject(project, filter, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Function> searchFunctionsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<Function> filter
    ) {
        log.debug("search functions for project {} with {} page {}", project, String.valueOf(filter), pageable);

        try {
            return entityService.searchByProject(project, filter, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Function> findFunctions(@NotNull String project, @NotNull String name) {
        log.debug("find functions for project {} with name {}", project, name);

        try {
            return versionableService.findAll(project, name);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Function> findFunctions(@NotNull String project, @NotNull String name, Pageable pageable) {
        log.debug("find functions for project {} with name {} page {}", project, name, pageable);

        try {
            return versionableService.findAll(project, name, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Function findFunction(@NotNull String id) {
        log.debug("find function with id {}", String.valueOf(id));
        try {
            return entityService.find(id);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Function getFunction(@NotNull String id) throws NoSuchEntityException {
        log.debug("get function with id {}", String.valueOf(id));

        try {
            return entityService.get(id);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.FUNCTION.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Function getLatestFunction(@NotNull String project, @NotNull String name) throws NoSuchEntityException {
        log.debug("get latest function for project {} with name {}", project, name);
        try {
            return versionableService.getLatest(project, name);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Function createFunction(@NotNull Function dto)
        throws DuplicatedEntityException, BindException, IllegalArgumentException {
        log.debug("create function");
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
    public Function updateFunction(@NotNull String id, @NotNull Function dto)
        throws NoSuchEntityException, BindException, IllegalArgumentException {
        log.debug("update function with id {}", String.valueOf(id));
        try {
            return entityService.update(id, dto);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.FUNCTION.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Deprecated(forRemoval = true)
    @Override
    public Function updateFunction(@NotNull String id, @NotNull Function dto, boolean force)
        throws NoSuchEntityException {
        log.debug("force update function with id {}", String.valueOf(id));
        try {
            //force update
            //no validation
            return entityRepository.update(id, dto);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.FUNCTION.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteFunction(@NotNull String id, @Nullable Boolean cascade) {
        log.debug("delete function with id {}", String.valueOf(id));

        try {
            entityService.delete(id, cascade);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteFunctions(@NotNull String project, @NotNull String name) {
        log.debug("delete functions for project {} with name {}", project, name);
        try {
            versionableService.deleteAll(project, name, true);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteFunctionsByProject(@NotNull String project) {
        log.debug("delete functions for project {}", project);
        try {
            entityService.deleteByProject(project, true);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Task> getTasksByFunctionId(@NotNull String functionId) {
        log.debug("list tasks for function {}", functionId);
        try {
            Function function = entityService.find(functionId);
            if (function == null) {
                return Collections.emptyList();
            }

            //define a spec for tasks building function path
            String path =
                (function.getKind() +
                    "://" +
                    function.getProject() +
                    "/" +
                    function.getName() +
                    ":" +
                    function.getId());

            Specification<TaskEntity> where = Specification.allOf(
                CommonSpecification.projectEquals(function.getProject()),
                createFunctionSpecification(path)
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

    private Specification<TaskEntity> createFunctionSpecification(String function) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.equal(root.get(Fields.FUNCTION), function);
        };
    }
}
