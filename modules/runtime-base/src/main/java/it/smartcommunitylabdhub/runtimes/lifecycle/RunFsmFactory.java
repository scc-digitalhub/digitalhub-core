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

package it.smartcommunitylabdhub.runtimes.lifecycle;

import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.infrastructure.Runtime;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import it.smartcommunitylabdhub.lifecycle.BaseFsmFactory;
import it.smartcommunitylabdhub.runtimes.lifecycle.states.RunStateBuilt;
import it.smartcommunitylabdhub.runtimes.lifecycle.states.RunStateCompleted;
import it.smartcommunitylabdhub.runtimes.lifecycle.states.RunStateCreated;
import it.smartcommunitylabdhub.runtimes.lifecycle.states.RunStateDeleted;
import it.smartcommunitylabdhub.runtimes.lifecycle.states.RunStateDeleting;
import it.smartcommunitylabdhub.runtimes.lifecycle.states.RunStateError;
import it.smartcommunitylabdhub.runtimes.lifecycle.states.RunStatePending;
import it.smartcommunitylabdhub.runtimes.lifecycle.states.RunStateReady;
import it.smartcommunitylabdhub.runtimes.lifecycle.states.RunStateRunning;
import it.smartcommunitylabdhub.runtimes.lifecycle.states.RunStateStop;
import it.smartcommunitylabdhub.runtimes.lifecycle.states.RunStateStopped;
import lombok.extern.slf4j.Slf4j;

/**
 * State machine factory for runs
 */
@Slf4j
public class RunFsmFactory<S extends RunBaseSpec, Z extends RunBaseStatus, R extends RunRunnable>
    extends BaseFsmFactory<Run, RunState, RunEvent> {

    public RunFsmFactory(Runtime<S, Z, R> runtime) {
        //build default states, runs are not resumable by default
        super(
            new RunStateBuilt<>(runtime),
            new RunStateCompleted<>(runtime),
            new RunStateCreated<>(runtime),
            new RunStateDeleted<>(runtime),
            new RunStateDeleting<>(runtime),
            new RunStateError<>(runtime),
            new RunStateReady<>(runtime),
            new RunStatePending<>(runtime),
            new RunStateRunning<>(runtime),
            new RunStateStop<>(runtime),
            new RunStateStopped<>(runtime)
        );
    }
}
