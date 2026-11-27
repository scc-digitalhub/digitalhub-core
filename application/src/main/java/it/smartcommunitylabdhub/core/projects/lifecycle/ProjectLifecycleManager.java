/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.core.projects.lifecycle;

import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.core.lifecycle.BaseLifecycleManager;
import it.smartcommunitylabdhub.core.projects.service.ProjectChildrenFinalizer;
import it.smartcommunitylabdhub.projects.Project;
import it.smartcommunitylabdhub.projects.lifecycle.ProjectEvents;
import it.smartcommunitylabdhub.projects.lifecycle.ProjectState;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Project lifecycle: the project waits for its children.
 *
 * After the detached effects of DELETING (children deleted, shared content last), within the turn:
 * a child in ERROR fails the project (delete again to retry), children left mean another pass later,
 * no child left concludes the delete.
 */
@Service
@Slf4j
public class ProjectLifecycleManager extends BaseLifecycleManager<Project> {

    //lazy: the finalizer depends on the children services
    private ObjectProvider<ProjectChildrenFinalizer> childrenFinalizer;

    @Autowired
    public void setChildrenFinalizer(ObjectProvider<ProjectChildrenFinalizer> childrenFinalizer) {
        this.childrenFinalizer = childrenFinalizer;
    }

    @Override
    public <I extends Serializable, R extends Serializable> Project perform(
        @NotNull Project dto,
        @NotNull String event,
        I input,
        BiConsumer<Project, R> effect
    ) {
        BiConsumer<Project, R> callback = (project, output) -> {
            if (ProjectEvents.DELETE.name().equals(event) && ProjectState.DELETING.name().equals(state(project))) {
                conclude(project);
            }

            //if provided, execute the effect
            if (effect != null) {
                effect.accept(project, output);
            }
        };

        return super.perform(dto, event, input, callback);
    }

    private void conclude(Project project) {
        ProjectChildrenFinalizer finalizer = childrenFinalizer != null ? childrenFinalizer.getIfAvailable() : null;
        List<String> children = finalizer != null ? finalizer.remaining(project.getId()) : List.of();

        if (children.contains(ProjectState.ERROR.name())) {
            log.debug("project {} has children in error, move to error", project.getId());
            project.setStatus(
                MapUtils.mergeMultipleMaps(project.getStatus(), Map.of("message", "children in error"))
            );
            handle(project, ProjectState.ERROR.name());
        } else if (!children.isEmpty()) {
            //children left: run the detached delete again, later
            log.debug("project {} has {} children left, delete again", project.getId(), children.size());
            perform(project, ProjectEvents.DELETE.name());
        } else {
            log.debug("project {} has no children left, complete delete", project.getId());
            handle(project, ProjectState.DELETED.name());
        }
    }
}
