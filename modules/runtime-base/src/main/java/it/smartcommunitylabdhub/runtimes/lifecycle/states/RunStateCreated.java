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

package it.smartcommunitylabdhub.runtimes.lifecycle.states;

import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.infrastructure.Runtime;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.fsm.FsmState;
import it.smartcommunitylabdhub.fsm.Transition;
import it.smartcommunitylabdhub.runtimes.lifecycle.RunEvent;
import it.smartcommunitylabdhub.runtimes.lifecycle.RunState;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RunStateCreated<S extends RunBaseSpec, Z extends RunBaseStatus, R extends RunRunnable>
    extends BaseRunState<RunState, RunEvent, S, Z, R> {

    public RunStateCreated(Runtime<S, Z, R> runtime) {
        super(RunState.CREATED, runtime);
    }

    @Override
    public FsmState<RunState, RunEvent, Run> build() {
        //define state
        RunState state = RunState.CREATED;

        //transitions
        List<Transition<RunState, RunEvent, Run>> txs = List.of(
            //(BUILD)->BUILT
            toBuilt().build(),
            //(ERROR)->ERROR
            toError().build(),
            //(DELETE)->DELETING
            toDeleting().build()
        );

        return new FsmState<>(state, txs);
    }

    protected Transition.Builder<RunState, RunEvent, Run> toBuilt() {
        //(BUILD)->BUILT
        return new Transition.Builder<RunState, RunEvent, Run>()
            .event(RunEvent.BUILD)
            .nextState(RunState.BUILT)
            .<R, R>withInternalLogic((currentState, nextState, event, run, i) -> {
                RunSpecAccessor specAccessor = RunSpecAccessor.with(run.getSpec());
                if (specAccessor.isLocalExecution()) {
                    return Optional.empty();
                }

                //build via runtime
                Optional.ofNullable(runtime.build(run)).ifPresent(spec -> run.setSpec(spec.toMap()));

                //callback for metadata update
                Optional
                    .ofNullable(runtime.onBuilt(run))
                    .ifPresent(meta -> run.setMetadata(MapUtils.mergeMultipleMaps(run.getMetadata(), meta.toMap())));

                return Optional.empty();
            });
    }
}
