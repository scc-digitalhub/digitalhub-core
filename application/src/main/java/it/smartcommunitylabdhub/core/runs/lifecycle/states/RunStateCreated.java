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

package it.smartcommunitylabdhub.core.runs.lifecycle.states;

import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.models.base.Executable;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.services.FunctionManager;
import it.smartcommunitylabdhub.commons.services.TaskService;
import it.smartcommunitylabdhub.commons.services.WorkflowManager;
import it.smartcommunitylabdhub.core.runs.lifecycle.RunContext;
import it.smartcommunitylabdhub.core.runs.lifecycle.RunEvent;
import it.smartcommunitylabdhub.fsm.FsmState;
import it.smartcommunitylabdhub.fsm.Transition;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RunStateCreated implements FsmState.Builder<State, RunEvent, RunContext, RunRunnable> {

    @Autowired
    private TaskService taskService;

    @Autowired
    private FunctionManager functionService;

    @Autowired
    private WorkflowManager workflowService;

    public FsmState<State, RunEvent, RunContext, RunRunnable> build() {
        //define state
        State state = State.CREATED;

        //transitions
        List<Transition<State, RunEvent, RunContext, RunRunnable>> txs = List.of(
            //(BUILD)->BUILT
            new Transition.Builder<State, RunEvent, RunContext, RunRunnable>()
                .event(RunEvent.BUILD)
                .nextState(State.BUILT)
                .withInternalLogic((currentState, nextState, event, context, runnable) -> {
                    RunSpecAccessor specAccessor = RunSpecAccessor.with(context.run.getSpec());
                    if (specAccessor.isLocalExecution()) {
                        return Optional.empty();
                    }

                    //retrieve executable
                    Task task = taskService.getTask(specAccessor.getTaskId());

                    Executable function = specAccessor.getWorkflowId() != null
                        ? workflowService.getWorkflow(specAccessor.getWorkflowId())
                        : functionService.getFunction(specAccessor.getFunctionId());

                    //build via runtime
                    return Optional.of(context.runtime.build(function, task, context.run));
                })
                .build(),
            //(ERROR)->ERROR
            new Transition.Builder<State, RunEvent, RunContext, RunRunnable>()
                .event(RunEvent.ERROR)
                .nextState(State.ERROR)
                .withInternalLogic((currentState, nextState, event, context, runnable) -> {
                    //no-op, nothing happened yet
                    return Optional.empty();
                })
                .build(),
            //(DELETING)->DELETING
            new Transition.Builder<State, RunEvent, RunContext, RunRunnable>()
                .event(RunEvent.DELETING)
                .nextState(State.DELETING)
                .withInternalLogic((currentState, nextState, event, context, runnable) -> {
                    //no-op, nothing happened yet
                    return Optional.empty();
                })
                .build()
        );

        return new FsmState<>(state, txs);
    }
}
