/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.core.workflows.service;

import it.smartcommunitylabdhub.commons.annotations.common.EffectType;
import it.smartcommunitylabdhub.commons.infrastructure.Effect;
import it.smartcommunitylabdhub.workflows.Workflow;
import it.smartcommunitylabdhub.tasks.Task;
import it.smartcommunitylabdhub.tasks.TaskService;
import it.smartcommunitylabdhub.tasks.filters.TaskEntityFilter;
import jakarta.annotation.Nullable;
import java.io.Serializable;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Workflow finalizer: delete the tasks of the workflow, with cascade (runs).
 *
 * TODO also track and cleanup runs: wait for the runs of the tasks to be gone before concluding
 */
@EffectType(stages = { "onDeleting" }, type = Workflow.class)
@Component
@Slf4j
public class WorkflowTasksFinalizer implements Effect<Workflow> {

    @Autowired
    private TaskService taskService;

    @Override
    public void process(String stage, Workflow workflow, @Nullable Serializable output) {
        log.debug("finalize workflow with id {}", workflow.getId());

        //tasks reference the workflow by path
        String path = workflow.getKind() + "://" + workflow.getProject() + "/" + workflow.getName() + ":" + workflow.getId();
        TaskEntityFilter filter = new TaskEntityFilter();
        filter.setWorkflow(path);

        List<Task> tasks = taskService.searchTasksByProject(workflow.getProject(), filter.toSearchFilter());

        //delete all with cascade, a failure does not stop the others (idempotent: retried by the loop)
        for (Task task : tasks) {
            try {
                taskService.deleteTask(task.getId(), true);
            } catch (RuntimeException e) {
                log.error("error deleting task {} of workflow {}: {}", task.getId(), workflow.getId(), e.getMessage());
            }
        }
    }
}
