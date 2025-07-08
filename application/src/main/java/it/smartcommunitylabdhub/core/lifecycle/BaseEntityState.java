/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * Copyright 2025 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.core.lifecycle;

import it.smartcommunitylabdhub.commons.lifecycle.LifecycleEvent;
import it.smartcommunitylabdhub.commons.lifecycle.LifecycleEvents;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.fsm.FsmState;
import it.smartcommunitylabdhub.fsm.Transition;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.util.Pair;

public class BaseEntityState<D extends BaseDTO & StatusDTO>
    implements FsmState.Builder<State, LifecycleEvents, LifecycleContext<D>, LifecycleEvent<D>> {

    protected final State state;
    protected final Set<Pair<LifecycleEvents, State>> nextStates;
    protected List<Transition<State, LifecycleEvents, LifecycleContext<D>, LifecycleEvent<D>>> txs;

    protected BaseEntityState(@NotNull State state, @Nullable Set<Pair<LifecycleEvents, State>> nextStates) {
        this.state = state;
        this.nextStates = nextStates == null ? Set.of() : nextStates;
    }

    protected BaseEntityState(
        @NotNull State state,
        @NotNull List<Transition<State, LifecycleEvents, LifecycleContext<D>, LifecycleEvent<D>>> txs
    ) {
        this.state = state;
        this.txs = txs;
        this.nextStates =
            txs
                .stream()
                .map(t -> Pair.of(t.getEvent(), t.getNextState()))
                .toList()
                .stream()
                .collect(Collectors.toSet());
    }

    @Override
    public FsmState<State, LifecycleEvents, LifecycleContext<D>, LifecycleEvent<D>> build() {
        if (txs != null) {
            return new FsmState<>(state, txs);
        }

        //build state transitions with no ops
        txs =
            nextStates
                .stream()
                .map(pair ->
                    new Transition.Builder<State, LifecycleEvents, LifecycleContext<D>, LifecycleEvent<D>>()
                        .event(pair.getFirst())
                        .nextState(pair.getSecond())
                        .withInternalLogic((currentState, nextState, event, context, input) -> {
                            //no-op, nothing happened yet
                            return Optional.empty();
                        })
                        .build()
                )
                .toList();

        return new FsmState<>(state, txs);
    }
}
