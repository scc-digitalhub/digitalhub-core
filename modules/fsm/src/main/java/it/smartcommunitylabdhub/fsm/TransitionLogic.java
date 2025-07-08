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
import java.util.Optional;

/**
 * This functional interface represents the internal logic of a transition
 *
 * @param <S> The type of the states.
 * @param <E> The type of the events.
 * @param <C> The type of the context.
 * @param <T> The type of the result from applying the logic.
 */
@FunctionalInterface
public interface TransitionLogic<S, E, C, I, R> {
    /**
     * Apply the internal logic of the state.
     *
     * @param context      The context for the state machine.
     * @param input        The input for the state machine.
     * @param stateMachine The state machine instance.
     * @return The optional result from applying the logic.
     */
    Optional<R> apply(S currentState, S nextState, E event, @Nullable C context, @Nullable I input);
}
