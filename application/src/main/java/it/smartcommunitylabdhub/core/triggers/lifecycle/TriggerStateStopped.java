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

package it.smartcommunitylabdhub.core.triggers.lifecycle;

import it.smartcommunitylabdhub.commons.infrastructure.TriggerRun;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerEvent;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerJob;
import it.smartcommunitylabdhub.fsm.FsmState;
import it.smartcommunitylabdhub.fsm.Transition;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TriggerStateStopped
    implements FsmState.Builder<State, TriggerEvent, TriggerContext, TriggerRun<? extends TriggerJob>> {

    public FsmState<State, TriggerEvent, TriggerContext, TriggerRun<? extends TriggerJob>> build() {
        //define state
        State state = State.STOPPED;

        //transitions
        List<Transition<State, TriggerEvent, TriggerContext, TriggerRun<? extends TriggerJob>>> txs = List.of(
            //(RUN)->RUNNING
            new Transition.Builder<State, TriggerEvent, TriggerContext, TriggerRun<? extends TriggerJob>>()
                .event(TriggerEvent.RUN)
                .nextState(State.RUNNING)
                .withInternalLogic((currentState, nextState, event, context, trigger) -> {
                    //run callback
                    return Optional.ofNullable(context.actuator.run(context.trigger));
                })
                .build(),
            new Transition.Builder<State, TriggerEvent, TriggerContext, TriggerRun<? extends TriggerJob>>()
                .event(TriggerEvent.ERROR)
                .nextState(State.ERROR)
                .withInternalLogic((currentState, nextState, event, context, runnable) -> {
                    //no-op, nothing happened yet
                    return Optional.empty();
                })
                .build()
        );

        return new FsmState<>(state, txs);
    }
}
