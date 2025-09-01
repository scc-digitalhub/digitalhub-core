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

package it.smartcommunitylabdhub.core.workflows.listeners;

import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.models.workflow.Workflow;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.core.events.AbstractEntityListener;
import it.smartcommunitylabdhub.core.events.EntityEvent;
import it.smartcommunitylabdhub.core.workflows.persistence.WorkflowEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.core.convert.converter.Converter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class WorkflowEntityListener extends AbstractEntityListener<WorkflowEntity, Workflow> {

    private EntityRepository<Project> projectService;

    public WorkflowEntityListener(Converter<WorkflowEntity, Workflow> converter) {
        super(converter);
    }

    @Autowired
    public void setProjectService(EntityRepository<Project> projectService) {
        this.projectService = projectService;
    }

    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void receive(EntityEvent<WorkflowEntity> event) {
        if (event.getEntity() == null) {
            return;
        }

        //handle
        super.handle(event);

        //update project date
        if (projectService != null) {
            String projectId = event.getEntity().getProject();
            log.debug("touch update project {}", projectId);
            try {
                Project project = projectService.find(projectId);
                if (project != null) {
                    //touch to set updated
                    projectService.update(project.getId(), project);
                }
            } catch (StoreException | IllegalArgumentException | NoSuchEntityException e) {
                log.error("store error", e.getMessage());
            }
        }
    }
}
