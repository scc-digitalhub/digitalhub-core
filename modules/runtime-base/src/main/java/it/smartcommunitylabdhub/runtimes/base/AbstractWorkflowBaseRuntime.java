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

import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.models.workflow.Workflow;
import it.smartcommunitylabdhub.commons.models.workflow.WorkflowBaseSpec;
import it.smartcommunitylabdhub.core.repositories.EntityRepository;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public abstract class AbstractWorkflowBaseRuntime<
    F extends WorkflowBaseSpec, S extends RunBaseSpec, Z extends RunBaseStatus, R extends RunRunnable
>
    extends AbstractBaseRuntime<S, Z, R> {

    protected EntityRepository<Workflow> workflowRepository;
    protected EntityRepository<Task> taskRepository;

    @Autowired
    public void setWorkflowRepository(EntityRepository<Workflow> workflowRepository) {
        this.workflowRepository = workflowRepository;
    }

    @Autowired
    public void setTaskRepository(EntityRepository<Task> taskRepository) {
        this.taskRepository = taskRepository;
    }

    public abstract S build(@NotNull Workflow workflowSpec, @NotNull Task taskSpec, @NotNull Run runSpec);

    @Override
    public S build(@NotNull Run run) {
        //check run kind
        if (!isSupported(run)) {
            throw new IllegalArgumentException("Run kind {} unsupported".formatted(String.valueOf(run.getKind())));
        }

        try {
            RunSpecAccessor specAccessor = RunSpecAccessor.with(run.getSpec());

            //retrieve executable
            Task task = taskRepository.get(specAccessor.getTaskId());

            Workflow workflow = workflowRepository.get(specAccessor.getWorkflowId());

            //build
            return build(workflow, task, run);
        } catch (NoSuchEntityException | StoreException e) {
            throw new CoreRuntimeException("runtime error building run spec", e);
        }
    }
}
