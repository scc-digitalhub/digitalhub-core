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

package it.smartcommunitylabdhub.artifacts.listeners;

import it.smartcommunitylabdhub.artifacts.Artifact;
import it.smartcommunitylabdhub.artifacts.persistence.ArtifactEntity;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.core.events.AbstractEntityListener;
import it.smartcommunitylabdhub.core.events.EntityEvent;
import it.smartcommunitylabdhub.files.service.FilesInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class ArtifactEntityListener extends AbstractEntityListener<ArtifactEntity, Artifact> {

    private EntityRepository<Project> projectService;
    private FilesInfoService filesInfoService;

    public ArtifactEntityListener(Converter<ArtifactEntity, Artifact> converter) {
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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void receive(EntityEvent<ArtifactEntity> event) {
        if (event.getEntity() == null) {
            return;
        }
        super.dispatch(event);
    }

    @Override
    public void handle(Message<EntityEvent<ArtifactEntity>> message) {
        // index + relationships
        super.handle(message);

        EntityEvent<ArtifactEntity> event = message.getPayload();
        ArtifactEntity entity = event.getEntity();
        ArtifactEntity prev = event.getPrev();
        if (log.isTraceEnabled()) {
            log.trace("{}: {}", clazz.getSimpleName(), String.valueOf(entity));
        }

        //update project date
        if (projectService != null) {
            String projectId = entity.getProject();
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
        if (prev == null || (prev != null && !entity.getState().equals(prev.getState()))) {
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
    protected void onDelete(ArtifactEntity entity, Artifact dto) {
        super.onDelete(entity, dto);

        //delete files info
        if (filesInfoService != null) {
            try {
                filesInfoService.clearFilesInfo(EntityUtils.getEntityName(Artifact.class), entity.getId());
            } catch (StoreException e) {
                log.error("store error", e.getMessage());
            }
        }
    }
}
