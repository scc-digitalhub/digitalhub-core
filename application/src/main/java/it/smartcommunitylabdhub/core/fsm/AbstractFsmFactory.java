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

package it.smartcommunitylabdhub.core.fsm;

import it.smartcommunitylabdhub.fsm.Fsm;
import it.smartcommunitylabdhub.fsm.FsmState;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * State machine factory
 */
@Slf4j
public abstract class AbstractFsmFactory<S, E, C, I> implements Fsm.Factory<S, E, C, I> {

    //states are defined only *once*
    private final List<FsmState.Builder<S, E, C, I>> stateBuilders;

    protected AbstractFsmFactory(List<FsmState.Builder<S, E, C, I>> stateBuilders) {
        this.stateBuilders = stateBuilders != null ? stateBuilders : Collections.emptyList();
    }

    /**
     * Create and configure the StateMachine for managing the state transitions of a Run.
     *
     * @param initialState   The initial state for the StateMachine.
     * @param initialContext The initial context for the StateMachine.
     * @return The configured StateMachine instance.
     */
    public Fsm<S, E, C, I> create(S initialState, C context) {
        // Create a new StateMachine builder with the initial state and context
        Fsm.Builder<S, E, C, I> builder = new Fsm.Builder<>(initialState, context);

        //add all states
        stateBuilders.forEach(sb -> {
            FsmState<S, E, C, I> state = sb.build();
            builder.withState(state.getState(), state);
        });

        //build to seal
        return builder.build();
    }
}
