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
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.function.FunctionBaseSpec;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.core.repositories.EntityRepository;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public abstract class AbstractFunctionBaseRuntime<
    F extends FunctionBaseSpec, S extends RunBaseSpec, Z extends RunBaseStatus, R extends RunRunnable
>
    extends AbstractBaseRuntime<S, Z, R> {

    protected EntityRepository<Function> functionRepository;
    protected EntityRepository<Task> taskRepository;

    @Autowired
    public void setFunctionRepository(EntityRepository<Function> functionRepository) {
        this.functionRepository = functionRepository;
    }

    @Autowired
    public void setTaskRepository(EntityRepository<Task> taskRepository) {
        this.taskRepository = taskRepository;
    }

    public abstract S build(@NotNull Function functionSpec, @NotNull Task taskSpec, @NotNull Run runSpec);

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

            Function function = functionRepository.get(specAccessor.getFunctionId());

            //build
            return build(function, task, run);
        } catch (NoSuchEntityException | StoreException e) {
            throw new CoreRuntimeException("runtime error building run spec", e);
        }
    }
}
