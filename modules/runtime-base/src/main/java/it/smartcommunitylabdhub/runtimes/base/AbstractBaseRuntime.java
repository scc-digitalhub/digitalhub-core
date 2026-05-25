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

package it.smartcommunitylabdhub.runtimes.base;

import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runs.lifecycle.RunState;
import it.smartcommunitylabdhub.runs.specs.RunBaseSpec;
import it.smartcommunitylabdhub.runs.specs.RunBaseStatus;
import it.smartcommunitylabdhub.runtimes.Runtime;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

@Slf4j
public abstract class AbstractBaseRuntime<
    S extends RunBaseSpec,
    Z extends RunBaseStatus,
    R extends RunRunnable
> implements Runtime<S, Z, R> {

    public abstract boolean isSupported(@NotNull Run run);

    @Override
    public R stop(@NotNull Run run) {
        //check run kind
        if (!isSupported(run)) {
            throw new IllegalArgumentException("Run kind {} unsupported".formatted(String.valueOf(run.getKind())));
        }

        //non-local are not manageable
        RunSpecAccessor specAccessor = RunSpecAccessor.with(run.getSpec());
        if (!specAccessor.isLocalExecution()) {
            //build runnable same as run
            R runnable = run(run);

            if (runnable != null) {
                runnable.setState(RunState.STOP.name());
                runnable.setMessage("stopping runnable " + runnable.getId());
                return runnable;
            }
        }

        log.warn("Error stopping run {}", run.getId());
        throw new NoSuchEntityException("Error stopping run");
    }

    @Override
    public R resume(@NotNull Run run) {
        //check run kind
        if (!isSupported(run)) {
            throw new IllegalArgumentException("Run kind {} unsupported".formatted(String.valueOf(run.getKind())));
        }

        //non-local are not manageable
        RunSpecAccessor specAccessor = RunSpecAccessor.with(run.getSpec());
        if (!specAccessor.isLocalExecution()) {
            //build runnable same as run
            R runnable = run(run);

            if (runnable != null) {
                runnable.setState(RunState.RESUME.name());
                runnable.setMessage("resuming runnable " + runnable.getId());
                return runnable;
            }
        }

        log.warn("Error resuming run {}", run.getId());
        throw new NoSuchEntityException("Error resuming run");
    }

    @Override
    @Nullable
    public R delete(@NotNull Run run) {
        //check run kind
        if (!isSupported(run)) {
            throw new IllegalArgumentException("Run kind {} unsupported".formatted(String.valueOf(run.getKind())));
        }

        try {
            //non-local are not manageable
            RunSpecAccessor specAccessor = RunSpecAccessor.with(run.getSpec());
            RunBaseStatus status = RunBaseStatus.with(run.getStatus());
            if (
                !specAccessor.isLocalExecution() &&
                status.getState() != null &&
                !RunState.CREATED.name().equals(status.getState())
            ) {
                //build runnable same as run
                R runnable = run(run);

                if (runnable != null) {
                    runnable.setState(RunState.DELETING.name());
                    runnable.setMessage("deleting runnable " + runnable.getId());
                    return runnable;
                }
            }
        } catch (RuntimeException e) {
            //build or runners may throw errors:  we log and swallow the exception to allow deletion to proceed,
            // we want to avoid blocking deletion due to runtime errors, even at the cost of leaving orphaned runnables
            log.warn("Error deleting runnable {}, swallowing exception: {}", run.getId(), e.getMessage());
        }

        //nothing to do
        return null;
    }

    @Override
    public Z onDeleted(@NotNull Run run, @Nullable RunRunnable runnable) {
        return null;
    }
}
