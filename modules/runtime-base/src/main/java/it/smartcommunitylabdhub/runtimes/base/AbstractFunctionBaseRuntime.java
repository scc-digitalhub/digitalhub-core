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

package it.smartcommunitylabdhub.runtimes.base;

import it.smartcommunitylabdhub.commons.Fields;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.function.FunctionBaseSpec;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.services.TaskService;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.functions.FunctionManager;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runs.specs.RunBaseSpec;
import it.smartcommunitylabdhub.runs.specs.RunBaseStatus;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;

@Slf4j
public abstract class AbstractFunctionBaseRuntime<
    F extends FunctionBaseSpec,
    S extends RunBaseSpec,
    Z extends RunBaseStatus,
    R extends RunRunnable
> extends AbstractBaseRuntime<S, Z, R> {

    private FunctionManager functionManager;
    private TaskService taskService;

    @Autowired
    public void setFunctionManager(FunctionManager functionManager) {
        this.functionManager = functionManager;
    }

    @Autowired
    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }

    public abstract S build(@NotNull Function functionSpec, @NotNull Task taskSpec, @NotNull Run runSpec);

    @Override
    public S build(@NotNull Run run) {
        //check run kind
        if (!isSupported(run)) {
            throw new IllegalArgumentException("Run kind {} unsupported".formatted(String.valueOf(run.getKind())));
        }

        try {
            //read spec
            RunSpecAccessor specAccessor = RunSpecAccessor.with(run.getSpec());

            //run must reference a specific task for a function, either via taskId or function + task kind
            if (specAccessor.getTaskId() != null) {
                //favour task reference, this must be valid
                Task task = taskService.getTask(specAccessor.getTaskId());
                TaskSpecAccessor taskSpecAccessor = TaskSpecAccessor.with(task.getSpec());

                if (taskSpecAccessor.getFunctionId() == null) {
                    throw new IllegalArgumentException("Task does not reference a function");
                }

                Function function = functionManager.getFunction(taskSpecAccessor.getFunctionId());

                return build(function, task, run);
            } else if (specAccessor.getFunctionId() != null && specAccessor.getTask() != null) {
                //dereference task
                Function function = functionManager.getFunction(specAccessor.getFunctionId());
                List<Task> tasks = functionManager.getTasksByFunctionId(function.getId());
                Task task = tasks
                    .stream()
                    .filter(t -> t.getKind().equals(specAccessor.getTask()))
                    .findFirst()
                    .orElse(null);

                if (task == null) {
                    //create a blank spec task when missing
                    Task t = new Task();
                    t.setKind(specAccessor.getTask());
                    t.setProject(function.getProject());
                    t.setSpec(
                        Map.of(
                            Fields.FUNCTION,
                            TaskSpecAccessor.build(
                                function.getKind(),
                                function.getProject(),
                                function.getName(),
                                function.getId()
                            )
                        )
                    );

                    try {
                        task = taskService.createTask(t);
                    } catch (IllegalArgumentException | SystemException | DuplicatedEntityException | BindException e) {
                        log.error("Error auto-creating task: {}", e.getMessage());
                        throw new StoreException("Error auto-creating task", e);
                    }

                    //patch run spec with task reference
                    run.setSpec(
                        MapUtils.mergeMultipleMaps(
                            run.getSpec(),
                            Map.of(Fields.TASK, RunSpecAccessor.build(task.getKind(), task.getProject(), task.getId()))
                        )
                    );

                    return build(function, task, run);
                }
            }

            throw new IllegalArgumentException("Run spec must reference a specific task or function");
        } catch (NoSuchEntityException | StoreException e) {
            throw new CoreRuntimeException("runtime error building run spec", e);
        }
    }
}
