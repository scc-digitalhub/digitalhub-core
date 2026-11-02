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
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.models.workflow.Workflow;
import it.smartcommunitylabdhub.commons.models.workflow.WorkflowBaseSpec;
import it.smartcommunitylabdhub.commons.services.TaskService;
import it.smartcommunitylabdhub.commons.services.WorkflowManager;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
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
public abstract class AbstractWorkflowBaseRuntime<
    F extends WorkflowBaseSpec,
    S extends RunBaseSpec,
    Z extends RunBaseStatus,
    R extends RunRunnable
> extends AbstractBaseRuntime<S, Z, R> {

    private WorkflowManager workflowManager;
    private TaskService taskService;

    @Autowired
    public void setWorkflowManager(WorkflowManager workflowManager) {
        this.workflowManager = workflowManager;
    }

    @Autowired
    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }

    public abstract S build(@NotNull Workflow workflowSpec, @NotNull Task taskSpec, @NotNull Run runSpec);

    @Override
    public S build(@NotNull Run run) {
        //check run kind
        if (!isSupported(run)) {
            throw new IllegalArgumentException("Run kind {} unsupported".formatted(String.valueOf(run.getKind())));
        }

        try {
            //read spec
            RunSpecAccessor specAccessor = RunSpecAccessor.with(run.getSpec());

            //run must reference a specific task for a workflow, either via taskId or workflow + task kind
            if (specAccessor.getTaskId() != null) {
                log.debug("Building run spec for task ID {}", specAccessor.getTaskId());
                //favour task reference, this must be valid
                Task task = taskService.getTask(specAccessor.getTaskId());
                TaskSpecAccessor taskSpecAccessor = TaskSpecAccessor.with(task.getSpec());

                //sanity check
                if (taskSpecAccessor.getWorkflowId() == null) {
                    throw new IllegalArgumentException("Task does not reference a workflow");
                }
                if (
                    specAccessor.getWorkflowId() != null &&
                    !specAccessor.getWorkflowId().equals(taskSpecAccessor.getWorkflowId())
                ) {
                    throw new IllegalArgumentException("Workflow ID mismatch between task and run spec");
                }

                Workflow workflow = workflowManager.getWorkflow(taskSpecAccessor.getWorkflowId());
                if (log.isTraceEnabled()) {
                    log.trace("build for workflow {}", workflow);
                    log.trace("build for task {}", task);
                }

                return build(workflow, task, run);
            } else if (specAccessor.getWorkflow() != null && specAccessor.getTask() != null) {
                log.debug(
                    "Building run spec for workflow {} and task {}",
                    specAccessor.getWorkflow(),
                    specAccessor.getTask()
                );
                //dereference task
                Workflow workflow =
                    specAccessor.getWorkflowId() != null
                        ? workflowManager.getWorkflow(specAccessor.getWorkflowId())
                        : workflowManager.getLatestWorkflowByName(run.getProject(), specAccessor.getWorkflow());
                List<Task> tasks = workflowManager.getTasksByWorkflowId(workflow.getId());
                Task task = tasks
                    .stream()
                    .filter(t -> t.getKind().equals(specAccessor.getTask()))
                    .findFirst()
                    .orElse(null);

                if (task == null) {
                    //create a blank spec task when missing
                    Task t = new Task();
                    t.setKind(specAccessor.getTask());
                    t.setProject(workflow.getProject());
                    t.setSpec(
                        Map.of(
                            Fields.WORKFLOW,
                            TaskSpecAccessor.build(
                                workflow.getKind(),
                                workflow.getProject(),
                                workflow.getName(),
                                workflow.getId()
                            )
                        )
                    );

                    try {
                        task = taskService.createTask(t);
                    } catch (IllegalArgumentException | SystemException | DuplicatedEntityException | BindException e) {
                        log.error("Error auto-creating task: {}", e.getMessage());
                        throw new StoreException("Error auto-creating task", e);
                    }
                }
                //patch run spec with task reference
                run.setSpec(
                    MapUtils.mergeMultipleMaps(
                        run.getSpec(),
                        Map.of(
                            Fields.TASK,
                            RunSpecAccessor.build(task.getKind(), task.getProject(), task.getId()),
                            Fields.WORKFLOW,
                            TaskSpecAccessor.build(
                                workflow.getKind(),
                                workflow.getProject(),
                                workflow.getName(),
                                workflow.getId()
                            )
                        )
                    )
                );

                if (log.isTraceEnabled()) {
                    log.trace("build for workflow {}", workflow);
                    log.trace("build for task {}", task);
                }
                return build(workflow, task, run);
            }

            throw new IllegalArgumentException("Run spec must reference a specific task or function");
        } catch (NoSuchEntityException | StoreException e) {
            throw new CoreRuntimeException("runtime error building run spec", e);
        }
    }
}
