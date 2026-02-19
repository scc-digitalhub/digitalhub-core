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

package it.smartcommunitylabdhub.lifecycle;

import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.fsm.FsmState;
import it.smartcommunitylabdhub.fsm.Transition;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.util.Pair;

public class BaseEntityStateBuilder<D extends BaseDTO & StatusDTO> implements FsmState.Builder<String, String, D> {

    protected final String state;
    protected Set<Pair<String, String>> nextStates;
    protected List<Transition<String, String, D>> txs;

    public BaseEntityStateBuilder(@NotNull String state) {
        this.state = state;
    }

    public BaseEntityStateBuilder(@NotNull String state, @Nullable Set<Pair<String, String>> nextStates) {
        this.state = state;
        this.nextStates = nextStates == null ? Set.of() : nextStates;
    }

    public BaseEntityStateBuilder(@NotNull String state, @NotNull List<Transition<String, String, D>> txs) {
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
    public FsmState<String, String, D> build() {
        if (txs == null) {
            txs = new ArrayList<>();
        }

        //for all next states, if not already present as transition, add a no-op transition
        if (nextStates != null) {
            for (Pair<String, String> pair : nextStates) {
                if (
                    txs
                        .stream()
                        .noneMatch(t ->
                            t.getEvent().equals(pair.getFirst()) && t.getNextState().equals(pair.getSecond())
                        )
                ) {
                    txs.add(
                        new Transition.Builder<String, String, D>()
                            .event(pair.getFirst())
                            .nextState(pair.getSecond())
                            .build()
                    );
                }
            }
        }

        return new FsmState<>(state, txs);
    }

    public BaseEntityStateBuilder<D> state(Pair<String, String> nextState) {
        if (this.nextStates == null) {
            this.nextStates = new HashSet<>();
        }
        this.nextStates.add(nextState);
        return this;
    }

    public BaseEntityStateBuilder<D> tx(Transition<String, String, D> tx) {
        if (this.txs == null) {
            this.txs = new ArrayList<>();
        }
        this.txs.add(tx);
        return this;
    }
}
