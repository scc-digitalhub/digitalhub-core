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

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Transition representation: an event (edge) towards a new state (node).
 * Can define internal logic to handle side effects as callback
 *
 * @param <S> The type of the states.
 * @param <E> The type of the events.
 * @param <C> The type of the context.
 * @param <I> The type of the input (side effect)
 * @param <R> The type of the return (side effect)
 */

@AllArgsConstructor
public class Transition<S, E, C> {

    // event definition
    @Getter
    @NotNull
    private final E event;

    // destination state
    @Getter
    @NotNull
    private final S nextState;

    @Nullable
    private final TransitionLogic<S, E, C, ?, ?> internalLogic;

    @SuppressWarnings("unchecked")
    public <I, R> Optional<TransitionLogic<S, E, C, I, R>> getInternalLogic() {
        return Optional.ofNullable((TransitionLogic<S, E, C, I, R>) internalLogic);
    }

    /**
     * Builder
     */
    public static class Builder<S, E, C> {

        private E event;
        private S nextState;
        private TransitionLogic<S, E, C, ?, ?> internalLogic = null;

        // public Builder() {}

        public Builder<S, E, C> event(E event) {
            this.event = event;
            return this;
        }

        public Builder<S, E, C> nextState(S nextState) {
            this.nextState = nextState;
            return this;
        }

        public <I, R> Builder<S, E, C> withInternalLogic(@Nullable TransitionLogic<S, E, C, I, R> internalLogic) {
            this.internalLogic = internalLogic;
            return this;
        }

        public Transition<S, E, C> build() {
            return new Transition<>(event, nextState, internalLogic);
        }
    }
}
