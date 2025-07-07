/**
 * Copyright 2025 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.core.projects.service;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.artifact.Artifact;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.dataitem.DataItem;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.model.Model;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.models.workflow.Workflow;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.commons.services.LabelService;
import it.smartcommunitylabdhub.commons.services.SecretService;
import it.smartcommunitylabdhub.core.services.EntityFinalizer;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProjectEntityFinalizer implements EntityFinalizer<Project>, InitializingBean {

    //NOTE: we can not autowire becase that will catch ProjectEntityServiceImpl as well
    private List<EntityService<? extends BaseDTO>> entityServices = new ArrayList<>();

    private LabelService labelService;
    private SecretService secretService;

    @Autowired(required = false)
    public void setFunctionService(EntityService<Function> functionService) {
        this.entityServices.add(functionService);
    }

    @Autowired(required = false)
    public void setArtifactService(EntityService<Artifact> artifactService) {
        this.entityServices.add(artifactService);
    }

    @Autowired(required = false)
    public void setDataItemService(EntityService<DataItem> dataItemService) {
        this.entityServices.add(dataItemService);
    }

    @Autowired(required = false)
    public void setModelService(EntityService<Model> modelService) {
        this.entityServices.add(modelService);
    }

    @Autowired(required = false)
    public void setWorkflowService(EntityService<Workflow> workflowService) {
        this.entityServices.add(workflowService);
    }

    @Autowired(required = false)
    public void setSecretService(SecretService secretService) {
        this.secretService = secretService;
    }

    @Autowired(required = false)
    public void setLabelService(LabelService labelService) {
        this.labelService = labelService;
    }

    @Override
    public void afterPropertiesSet() throws Exception {}

    @Override
    public void finalize(@NotNull Project project) throws StoreException {
        log.debug("finalize project with id {}", project.getId());

        String id = project.getId();

        //remove all entities related to the project, with cascade, in sync
        entityServices.forEach(service -> {
            try {
                if (service != null) {
                    log.debug("cascade delete for project {} with service {}", id, service);
                    service.deleteByProject(id, true);
                }
            } catch (StoreException e) {
                log.error(
                    "error deleting entities for project {} in service {}: {}",
                    id,
                    service.getClass().getSimpleName(),
                    e
                );
            }
        });

        if (secretService != null) {
            log.debug("cascade delete secrets for project with id {}", String.valueOf(id));
            secretService.deleteSecretsByProject(id);
        }

        if (labelService != null) {
            //remove labels for project
            log.debug("cascade delete labels for project with id {}", String.valueOf(id));
            labelService.deleteLabelsByProject(id);
        }
    }
}
