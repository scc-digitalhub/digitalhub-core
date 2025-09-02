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

package it.smartcommunitylabdhub.core.models.listeners;

import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.model.Model;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.core.events.AbstractEntityListener;
import it.smartcommunitylabdhub.core.events.EntityEvent;
import it.smartcommunitylabdhub.core.models.persistence.ModelEntity;
import it.smartcommunitylabdhub.files.service.FilesInfoService;
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
public class ModelEntityListener extends AbstractEntityListener<ModelEntity, Model> {

    private EntityRepository<Project> projectService;
    private FilesInfoService filesInfoService;

    public ModelEntityListener(Converter<ModelEntity, Model> converter) {
        super(converter);
    }

    @Autowired
    public void setProjectService(EntityRepository<Project> projectService) {
        this.projectService = projectService;
    }

    @Autowired
    public void setFilesInfoService(FilesInfoService filesInfoService) {
        this.filesInfoService = filesInfoService;
    }

    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void receive(EntityEvent<ModelEntity> event) {
        if (event.getEntity() == null) {
            return;
        }

        log.debug("receive event for {} {}", clazz.getSimpleName(), event.getAction());

        ModelEntity entity = event.getEntity();
        ModelEntity prev = event.getPrev();
        if (log.isTraceEnabled()) {
            log.trace("{}: {}", clazz.getSimpleName(), String.valueOf(entity));
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

        //notify user event if either: prev == null (for create/delete), prev != null and state has changed (update)
        if (prev == null || (prev != null && prev.getState() != entity.getState())) {
            //always broadcast updates
            super.broadcast(event);

            if (entity.getUpdatedBy() != null) {
                //notify user
                super.notify(entity.getUpdatedBy(), event);

                if (!entity.getUpdatedBy().equals(entity.getCreatedBy())) {
                    //notify owner
                    super.notify(entity.getCreatedBy(), event);
                }
            }
        }
    }

    @Override
    protected void onDelete(ModelEntity entity, Model dto) {
        super.onDelete(entity, dto);

        //delete files info
        if (filesInfoService != null) {
            try {
                filesInfoService.clearFilesInfo(EntityName.MODEL.getValue(), entity.getId());
            } catch (StoreException e) {
                log.error("store error", e.getMessage());
            }
        }
    }
}
