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
public class FsmState<S, E, C, I> {

    @Getter
    protected final S state;

    @Getter
    protected final List<Transition<S, E, C, I>> transitions;

    public FsmState(S state, List<Transition<S, E, C, I>> transitions) {
        this.state = state;
        this.transitions = transitions;
    }

    /**
     * Get the transition associated with the specified event, if present
     * @param event
     * @return
     */
    public Optional<Transition<S, E, C, I>> getTransitionForEvent(E event) {
        return transitions.stream().filter(t -> t.getEvent().equals(event)).findFirst();
    }

    /**
     * Get the transition associated with a given next state, if present
     *
     * @param nextState The next state for which to retrieve the transition event.
     * @return An Optional containing the transition event if found, or an empty Optional if not
     * found.
     */
    public Optional<Transition<S, E, C, I>> getTransitionForNext(S nextState) {
        return transitions.stream().filter(t -> t.getNextState().equals(nextState)).findFirst();
    }

    @FunctionalInterface
    public static interface Builder<S, E, C, I> {
        FsmState<S, E, C, I> build();
    }
    // /**
    //  * A builder class for constructing FsmState objects.
    //  *
    //  * @param <S> The type of the states.
    //  * @param <E> The type of the events.
    //  * @param <C> The type of the context.
    //  */
    // public static class Builder<S, E, C, I> {

    //     private final List<Transition<S, E, C, I>> transitions;
    //     private final S state;

    //     /**
    //      * Constructs a new StateBuilder object.
    //      *
    //      * @param state the name of the state
    //      */
    //     public Builder(S state) {
    //         this.state = state;
    //         transitions = new ArrayList<>();
    //     }

    //     /**
    //      * Add a transition associated with this state.
    //      *
    //      * @param transition The transition to add.
    //      * @return The StateBuilder instance.
    //      */
    //     public Builder<S, E, C, I> withTransition(Transition<S, E, C, I> transition) {
    //         transitions.add(transition);
    //         return this;
    //     }

    //     /**
    //      * Add a transition associated with this state.
    //      *
    //      * @param transitionList List of transitions
    //      * @return The StateBuilder instance.
    //      */
    //     public Builder<S, E, C, I> withTransitions(List<Transition<S, E, C, I>> transitionList) {
    //         transitions.addAll(transitionList);
    //         return this;
    //     }

    //     /**
    //      * Finalize the state definition and return the parent builder.
    //      *
    //      * @return The parent builder instance.
    //      */
    //     public FsmState<S, E, C, I> build() {
    //         return new FsmState<>(state, transitions);
    //     }
    // }
}
