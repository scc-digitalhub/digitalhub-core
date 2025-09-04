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

import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.infrastructure.Runtime;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import it.smartcommunitylabdhub.runtimes.lifecycle.RunEvent;
import it.smartcommunitylabdhub.runtimes.lifecycle.RunState;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RunStateDeleting<S extends RunBaseSpec, Z extends RunBaseStatus, R extends RunRunnable>
    extends BaseRunState<RunState, RunEvent, S, Z, R> {

    public RunStateDeleting(Runtime<S, Z, R> runtime) {
        super(RunState.DELETING, runtime);
        //transitions
        txs = List.of(toDelete().build(), toError().build());
    }
}
