/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.core.functions.service;

import it.smartcommunitylabdhub.commons.annotations.common.EffectType;
import it.smartcommunitylabdhub.commons.infrastructure.Effect;
import it.smartcommunitylabdhub.functions.Function;
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
 * Function finalizer: delete the tasks of the function, with cascade (runs).
 *
 * TODO also track and cleanup runs: wait for the runs of the tasks to be gone before concluding
 */
@EffectType(stages = { "onDeleting" }, type = Function.class)
@Component
@Slf4j
public class FunctionTasksFinalizer implements Effect<Function> {

    @Autowired
    private TaskService taskService;

    @Override
    public void process(String stage, Function function, @Nullable Serializable output) {
        log.debug("finalize function with id {}", function.getId());

        //tasks reference the function by path
        String path = function.getKind() + "://" + function.getProject() + "/" + function.getName() + ":" + function.getId();
        TaskEntityFilter filter = new TaskEntityFilter();
        filter.setFunction(path);

        List<Task> tasks = taskService.searchTasksByProject(function.getProject(), filter.toSearchFilter());

        //delete all with cascade, a failure does not stop the others (idempotent: retried by the loop)
        for (Task task : tasks) {
            try {
                taskService.deleteTask(task.getId(), true);
            } catch (RuntimeException e) {
                log.error("error deleting task {} of function {}: {}", task.getId(), function.getId(), e.getMessage());
            }
        }
    }
}
