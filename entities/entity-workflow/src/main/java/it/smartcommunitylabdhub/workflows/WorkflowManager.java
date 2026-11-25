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

package it.smartcommunitylabdhub.workflows;

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
 * Exposed manager for workflow-related operations via services
 */
@Component
@Validated
public class WorkflowManager {

    @Delegate(excludes = { EntityAwareService.class })
    private EntityService<Workflow> workflowEntityService;

    @Delegate(excludes = { EntityAwareService.class })
    private VersionableEntityService<Workflow> workflowVersionableEntityService;

    @Delegate(excludes = { EntityAwareService.class })
    private IndexableEntityService<Workflow> workflowIndexableEntityService;

    @Delegate(excludes = { EntityAwareService.class })
    private RelationshipsAwareEntityService<Workflow> workflowRelationshipsAwareEntityService;

    @Delegate(excludes = { EntityAwareService.class })
    private EntityFinalizer<Workflow> workflowEntityFinalizer;

    @Delegate(excludes = { EntityAwareService.class })
    private EntityOperationsListener<Workflow> workflowOperationsListener;

    @Delegate(excludes = { EntityAwareService.class })
    private SpecRegistry<Workflow> workflowSpecRegistry;

    @Delegate(excludes = { EntityAwareService.class })
    private SchemaService<Workflow> workflowSchemaService;

    private TaskService taskService;

    @Autowired
    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }

    @Autowired
    public void setWorkflowEntityService(EntityService<Workflow> workflowEntityService) {
        this.workflowEntityService = workflowEntityService;
    }

    @Autowired
    public void setWorkflowVersionableEntityService(
        VersionableEntityService<Workflow> workflowVersionableEntityService
    ) {
        this.workflowVersionableEntityService = workflowVersionableEntityService;
    }

    @Autowired
    public void setWorkflowIndexableEntityService(IndexableEntityService<Workflow> workflowIndexableEntityService) {
        this.workflowIndexableEntityService = workflowIndexableEntityService;
    }

    @Autowired
    public void setWorkflowRelationshipsAwareEntityService(
        RelationshipsAwareEntityService<Workflow> workflowRelationshipsAwareEntityService
    ) {
        this.workflowRelationshipsAwareEntityService = workflowRelationshipsAwareEntityService;
    }

    @Autowired
    public void setWorkflowEntityFinalizer(EntityFinalizer<Workflow> workflowEntityFinalizer) {
        this.workflowEntityFinalizer = workflowEntityFinalizer;
    }

    @Autowired
    public void setWorkflowOperationsListener(EntityOperationsListener<Workflow> workflowOperationsListener) {
        this.workflowOperationsListener = workflowOperationsListener;
    }

    @Autowired
    public void setWorkflowSpecRegistry(SpecRegistry<Workflow> workflowSpecRegistry) {
        this.workflowSpecRegistry = workflowSpecRegistry;
    }

    @Autowired
    public void setWorkflowSchemaService(SchemaService<Workflow> workflowSchemaService) {
        this.workflowSchemaService = workflowSchemaService;
    }

    /*
     * Additional methods
     */
    public List<Task> getTasksByWorkflowId(@NotNull String workflowId)
        throws SystemException, NoSuchEntityException, StoreException {
        Workflow workflow = workflowEntityService.get(workflowId);

        //search via key
        String fn = TaskSpecAccessor.build(
            workflow.getKind(),
            workflow.getProject(),
            workflow.getName(),
            workflow.getId()
        );
        TaskEntityFilter filter = new TaskEntityFilter();
        filter.setWorkflow(fn);

        return taskService.searchTasksByProject(workflow.getProject(), filter.toSearchFilter());
    }
}
