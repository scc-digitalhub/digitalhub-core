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
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import it.smartcommunitylabdhub.core.runs.lifecycle.RunContext;
import it.smartcommunitylabdhub.core.runs.lifecycle.RunEvent;
import it.smartcommunitylabdhub.fsm.FsmState;
import it.smartcommunitylabdhub.fsm.Transition;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RunStateStop implements FsmState.Builder<State, RunEvent, RunContext> {

    public FsmState<State, RunEvent, RunContext> build() {
        //define state
        State state = State.STOP;

        //transitions
        List<Transition<State, RunEvent, RunContext>> txs = List.of(
            //(STOP)->STOPPED
            new Transition.Builder<State, RunEvent, RunContext>()
                .event(RunEvent.STOP)
                .nextState(State.STOPPED)
                .<RunRunnable, RunBaseStatus>withInternalLogic((currentState, nextState, event, context, runnable) -> {
                    //runtime callback
                    return Optional.ofNullable(context.runtime.onStopped(context.run, runnable));
                })
                .build(),
            //(ERROR)->ERROR
            new Transition.Builder<State, RunEvent, RunContext>()
                .event(RunEvent.ERROR)
                .nextState(State.ERROR)
                .<RunRunnable, RunBaseStatus>withInternalLogic((currentState, nextState, event, context, runnable) -> {
                    RunSpecAccessor specAccessor = RunSpecAccessor.with(context.run.getSpec());
                    if (specAccessor.isLocalExecution()) {
                        return Optional.empty();
                    }

                    //error callback
                    return Optional.ofNullable(context.runtime.onError(context.run, runnable));
                })
                .build(),
            //(DELETING)->DELETING
            new Transition.Builder<State, RunEvent, RunContext>()
                .event(RunEvent.DELETING)
                .nextState(State.DELETING)
                .withInternalLogic((currentState, nextState, event, context, runnable) -> {
                    RunSpecAccessor specAccessor = RunSpecAccessor.with(context.run.getSpec());
                    if (specAccessor.isLocalExecution()) {
                        return Optional.empty();
                    }

                    //delete via runtime
                    return Optional.ofNullable(context.runtime.delete(context.run));
                })
                .build()
        );

        return new FsmState<>(state, txs);
    }
}
