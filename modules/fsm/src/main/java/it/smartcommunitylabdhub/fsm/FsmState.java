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

package it.smartcommunitylabdhub.fsm;

import java.util.List;
import java.util.Optional;
import lombok.Getter;

/*
 * Compact connected state representation, grouping state + transitions towards different states
 */
public class FsmState<S, E, C> {

    @Getter
    protected final S state;

    @Getter
    protected final List<Transition<S, E, C>> transitions;

    public FsmState(S state, List<Transition<S, E, C>> transitions) {
        this.state = state;
        this.transitions = transitions;
    }

    /**
     * Get the transition associated with the specified event, if present
     * @param event
     * @return
     */
    public Optional<Transition<S, E, C>> getTransitionForEvent(E event) {
        return transitions.stream().filter(t -> t.getEvent().equals(event)).findFirst();
    }

    /**
     * Get the transition associated with a given next state, if present
     *
     * @param nextState The next state for which to retrieve the transition event.
     * @return An Optional containing the transition event if found, or an empty Optional if not
     * found.
     */
    public Optional<Transition<S, E, C>> getTransitionForNext(S nextState) {
        return transitions.stream().filter(t -> t.getNextState().equals(nextState)).findFirst();
    }

    @FunctionalInterface
    public static interface Builder<S, E, C> {
        FsmState<S, E, C> build();
    }
}
