/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.core.projects.service;

import it.smartcommunitylabdhub.artifacts.Artifact;
import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.annotations.common.EffectType;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.infrastructure.Effect;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.commons.services.SecretService;
import it.smartcommunitylabdhub.containerimage.ContainerImage;
import it.smartcommunitylabdhub.dataitems.DataItem;
import it.smartcommunitylabdhub.functions.Function;
import it.smartcommunitylabdhub.labels.LabelService;
import it.smartcommunitylabdhub.models.Model;
import it.smartcommunitylabdhub.projects.Project;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.triggers.Trigger;
import it.smartcommunitylabdhub.workflows.Workflow;
import jakarta.annotation.Nullable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Project finalizer: delete every child, then the shared content (secrets, labels) once no child is
 * left. Children deletes run inline in the project detached execution: the project lifecycle
 * concludes when no child is left.
 */
@EffectType(stages = { "onDeleting" }, type = Project.class)
@Component
@Slf4j
public class ProjectChildrenFinalizer implements Effect<Project> {

    private static final String STATE_DELETING = "DELETING";

    //NOTE: we can not autowire a list because that will catch ProjectEntityServiceImpl as well
    private EntityService<Trigger> triggerService;
    private EntityService<Function> functionService;
    private EntityService<Workflow> workflowService;
    private EntityService<Run> runService;
    private EntityService<Artifact> artifactService;
    private EntityService<DataItem> dataItemService;
    private EntityService<Model> modelService;
    private EntityService<ContainerImage> containerImageService;

    private LabelService labelService;
    private SecretService secretService;

    @Autowired(required = false)
    public void setTriggerService(EntityService<Trigger> triggerService) {
        this.triggerService = triggerService;
    }

    @Autowired(required = false)
    public void setFunctionService(EntityService<Function> functionService) {
        this.functionService = functionService;
    }

    @Autowired(required = false)
    public void setWorkflowService(EntityService<Workflow> workflowService) {
        this.workflowService = workflowService;
    }

    @Autowired(required = false)
    public void setRunService(EntityService<Run> runService) {
        this.runService = runService;
    }

    @Autowired(required = false)
    public void setArtifactService(EntityService<Artifact> artifactService) {
        this.artifactService = artifactService;
    }

    @Autowired(required = false)
    public void setDataItemService(EntityService<DataItem> dataItemService) {
        this.dataItemService = dataItemService;
    }

    @Autowired(required = false)
    public void setModelService(EntityService<Model> modelService) {
        this.modelService = modelService;
    }

    @Autowired(required = false)
    public void setContainerImageService(EntityService<ContainerImage> containerImageService) {
        this.containerImageService = containerImageService;
    }

    @Autowired(required = false)
    public void setSecretService(SecretService secretService) {
        this.secretService = secretService;
    }

    @Autowired(required = false)
    public void setLabelService(LabelService labelService) {
        this.labelService = labelService;
    }

    /**
     * Children services, in deletion order: triggers first (stop firing), runs after the functions
     * and workflows which own them
     */
    private List<EntityService<? extends BaseDTO>> children() {
        return Stream.<EntityService<? extends BaseDTO>>of(
            triggerService,
            functionService,
            workflowService,
            runService,
            artifactService,
            dataItemService,
            modelService,
            containerImageService
        )
            .filter(s -> s != null)
            .toList();
    }

    @Override
    public void process(String stage, Project project, @Nullable Serializable output) {
        String id = project.getId();
        log.debug("finalize project with id {}", id);

        //delete every child not already being deleted: idempotent on every pass
        for (EntityService<? extends BaseDTO> service : children()) {
            List<? extends BaseDTO> items;
            try {
                items = service.listByProject(id);
            } catch (StoreException e) {
                log.error("error listing children of project {} in {}: {}", id, service.getClass().getSimpleName(), e.getMessage());
                continue;
            }

            for (BaseDTO item : items) {
                if (STATE_DELETING.equals(state(item))) {
                    continue;
                }

                try {
                    log.debug("cascade delete {} {} for project {}", item.getKind(), item.getId(), id);
                    service.delete(item.getId(), true);
                } catch (StoreException | RuntimeException e) {
                    log.error("error deleting {} for project {}: {}", item.getId(), id, e.getMessage());
                }
            }
        }

        //shared content last: only when no child is left
        if (!remaining(id).isEmpty()) {
            log.debug("project {} has children left, keep shared content for now", id);
            return;
        }

        if (secretService != null) {
            log.debug("cascade delete secrets for project with id {}", id);
            secretService.deleteSecretsByProject(id);
        }

        if (labelService != null) {
            log.debug("cascade delete labels for project with id {}", id);
            try {
                labelService.deleteLabelsByProject(id);
            } catch (StoreException e) {
                log.error("error deleting labels for project {}: {}", id, e.getMessage());
            }
        }
    }

    /**
     * States of the children still present for the project (UNKNOWN when they can not be read)
     */
    public List<String> remaining(String project) {
        List<String> states = new ArrayList<>();
        for (EntityService<? extends BaseDTO> service : children()) {
            try {
                service.listByProject(project).forEach(item -> states.add(state(item)));
            } catch (StoreException e) {
                log.error("error listing children of project {}: {}", project, e.getMessage());
                states.add("UNKNOWN");
            }
        }

        return states;
    }

    private static String state(BaseDTO dto) {
        return dto instanceof StatusDTO s ? StatusFieldAccessor.with(s.getStatus()).getState() : null;
    }
}
