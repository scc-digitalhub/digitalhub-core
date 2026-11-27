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

package it.smartcommunitylabdhub.functions;

import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.services.EntityAwareService;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.commons.services.SchemaService;
import it.smartcommunitylabdhub.commons.services.SpecRegistry;
import it.smartcommunitylabdhub.core.events.EntityOperationsListener;
import it.smartcommunitylabdhub.core.services.EntityFinalizer;
import it.smartcommunitylabdhub.core.services.VersionableEntityService;
import it.smartcommunitylabdhub.relationships.RelationshipsAwareEntityService;
import it.smartcommunitylabdhub.search.service.IndexableEntityService;
import it.smartcommunitylabdhub.tasks.Task;
import it.smartcommunitylabdhub.tasks.TaskService;
import it.smartcommunitylabdhub.tasks.filters.TaskEntityFilter;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.experimental.Delegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/*
 * Exposed manager for function-related operations via services
 */
@Component
@Validated
public class FunctionManager {

    @Delegate(excludes = { EntityAwareService.class })
    private EntityService<Function> functionEntityService;

    @Delegate(excludes = { EntityAwareService.class })
    private VersionableEntityService<Function> functionVersionableEntityService;

    @Delegate(excludes = { EntityAwareService.class })
    private IndexableEntityService<Function> functionIndexableEntityService;

    @Delegate(excludes = { EntityAwareService.class })
    private RelationshipsAwareEntityService<Function> functionRelationshipsAwareEntityService;

    @Delegate(excludes = { EntityAwareService.class })
    private EntityFinalizer<Function> functionEntityFinalizer;

    @Delegate(excludes = { EntityAwareService.class })
    private EntityOperationsListener<Function> functionOperationsListener;

    @Delegate(excludes = { EntityAwareService.class })
    private SpecRegistry<Function> functionSpecRegistry;

    @Delegate(excludes = { EntityAwareService.class })
    private SchemaService<Function> functionSchemaService;

    private TaskService taskService;

    @Autowired
    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }

    @Autowired
    public void setFunctionEntityService(EntityService<Function> functionEntityService) {
        this.functionEntityService = functionEntityService;
    }

    @Autowired
    public void setFunctionVersionableEntityService(
        VersionableEntityService<Function> functionVersionableEntityService
    ) {
        this.functionVersionableEntityService = functionVersionableEntityService;
    }

    @Autowired
    public void setFunctionIndexableEntityService(IndexableEntityService<Function> functionIndexableEntityService) {
        this.functionIndexableEntityService = functionIndexableEntityService;
    }

    @Autowired
    public void setFunctionRelationshipsAwareEntityService(
        RelationshipsAwareEntityService<Function> functionRelationshipsAwareEntityService
    ) {
        this.functionRelationshipsAwareEntityService = functionRelationshipsAwareEntityService;
    }

    @Autowired
    public void setFunctionEntityFinalizer(EntityFinalizer<Function> functionEntityFinalizer) {
        this.functionEntityFinalizer = functionEntityFinalizer;
    }

    @Autowired
    public void setFunctionOperationsListener(EntityOperationsListener<Function> functionOperationsListener) {
        this.functionOperationsListener = functionOperationsListener;
    }

    @Autowired
    public void setFunctionSpecRegistry(SpecRegistry<Function> functionSpecRegistry) {
        this.functionSpecRegistry = functionSpecRegistry;
    }

    @Autowired
    public void setFunctionSchemaService(SchemaService<Function> functionSchemaService) {
        this.functionSchemaService = functionSchemaService;
    }

    /*
     * Additional methods
     */
    public List<Task> getTasksByFunctionId(@NotNull String functionId)
        throws SystemException, NoSuchEntityException, StoreException {
        Function function = functionEntityService.get(functionId);

        //search via key
        String fn = TaskSpecAccessor.build(
            function.getKind(),
            function.getProject(),
            function.getName(),
            function.getId()
        );
        TaskEntityFilter filter = new TaskEntityFilter();
        filter.setFunction(fn);

        return taskService.searchTasksByProject(function.getProject(), filter.toSearchFilter());
    }
}
