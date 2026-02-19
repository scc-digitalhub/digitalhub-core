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
import it.smartcommunitylabdhub.runs.lifecycle.RunEvent;
import it.smartcommunitylabdhub.runs.lifecycle.RunState;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

@Slf4j
public class BaseRunState<X extends RunBaseSpec, Z extends RunBaseStatus, R extends RunRunnable>
    implements FsmState.Builder<String, String, Run> {

    protected final String state;
    protected final Runtime<X, Z, R> runtime;

    protected List<Transition<String, String, Run>> txs;

    public BaseRunState(String state, Runtime<X, Z, R> runtime) {
        Assert.notNull(state, "state is required");
        Assert.notNull(runtime, "runtime is required");

        this.state = state;
        this.runtime = runtime;
    }

    public FsmState<String, String, Run> build() {
        return new FsmState<>(state, txs);
    }

    //TODO evaluate splitting to factory classes
    protected Transition.Builder<String, String, Run> toError() {
        //(ERROR)->ERROR
        return new Transition.Builder<String, String, Run>()
            .event(RunEvent.ERROR.name())
            .nextState(RunState.ERROR.name())
            .<R, R>withInternalLogic((currentState, nextState, event, run, runnable) -> {
                RunSpecAccessor specAccessor = RunSpecAccessor.with(run.getSpec());
                if (specAccessor.isLocalExecution()) {
                    return Optional.empty();
                }

                //runtime callback
                Optional
                    .ofNullable(runtime.onError(run, runnable))
                    .ifPresent(status -> run.setStatus(MapUtils.mergeMultipleMaps(run.getStatus(), status.toMap())));

                //final state, cleanup
                if (runnable != null) {
                    return Optional.ofNullable(runtime.delete(run));
                }

                //no-op, nothing happened yet
                return Optional.empty();
            });
    }

    protected Transition.Builder<String, String, Run> toDeleting() {
        //(DELETE)->DELETING
        return new Transition.Builder<String, String, Run>()
            .event(RunEvent.DELETE.name())
            .nextState(RunState.DELETING.name())
            .<R, R>withInternalLogic((currentState, nextState, event, run, runnable) -> {
                RunSpecAccessor specAccessor = RunSpecAccessor.with(run.getSpec());
                if (specAccessor.isLocalExecution()) {
                    return Optional.empty();
                }

                //delete via runtime
                return Optional.ofNullable(runtime.delete(run));
            });
    }

    protected Transition.Builder<String, String, Run> toDelete() {
        //(DELETE)->DELETED
        return new Transition.Builder<String, String, Run>()
            .event(RunEvent.DELETE.name())
            .nextState(RunState.DELETED.name())
            .<R, R>withInternalLogic((currentState, nextState, event, run, runnable) -> {
                RunSpecAccessor specAccessor = RunSpecAccessor.with(run.getSpec());
                if (specAccessor.isLocalExecution()) {
                    return Optional.empty();
                }

                //runtime callback
                Optional
                    .ofNullable(runtime.onDeleted(run, runnable))
                    .ifPresent(status -> run.setStatus(MapUtils.mergeMultipleMaps(run.getStatus(), status.toMap())));

                return Optional.empty();
            });
    }

    protected Transition.Builder<String, String, Run> toReady() {
        //(RUN)->READY
        return new Transition.Builder<String, String, Run>()
            .event(RunEvent.RUN.name())
            .nextState(RunState.READY.name())
            .<R, R>withInternalLogic((currentState, nextState, event, run, i) -> {
                RunSpecAccessor specAccessor = RunSpecAccessor.with(run.getSpec());
                if (specAccessor.isLocalExecution()) {
                    return Optional.empty();
                }

                //run via runtime
                Optional<R> runnable = Optional.ofNullable(runtime.run(run));
                runnable.ifPresent(r -> {
                    //runtime callback
                    Optional
                        .ofNullable(runtime.onReady(run, r))
                        .ifPresent(status -> run.setStatus(MapUtils.mergeMultipleMaps(run.getStatus(), status.toMap()))
                        );
                });
                return runnable;
            });
    }

    protected Transition.Builder<String, String, Run> toPending() {
        //(EXECUTE)->RUNNING
        return new Transition.Builder<String, String, Run>()
            .event(RunEvent.SCHEDULE.name())
            .nextState(RunState.PENDING.name())
            .<R, R>withInternalLogic((currentState, nextState, event, run, runnable) -> {
                RunSpecAccessor specAccessor = RunSpecAccessor.with(run.getSpec());
                if (specAccessor.isLocalExecution()) {
                    return Optional.empty();
                }

                //runtime callback
                //TODO

                return Optional.empty();
            });
    }

    protected Transition.Builder<String, String, Run> toRunning() {
        //(EXECUTE)->RUNNING
        return new Transition.Builder<String, String, Run>()
            .event(RunEvent.EXECUTE.name())
            .nextState(RunState.RUNNING.name())
            .<R, R>withInternalLogic((currentState, nextState, event, run, runnable) -> {
                RunSpecAccessor specAccessor = RunSpecAccessor.with(run.getSpec());
                if (specAccessor.isLocalExecution()) {
                    return Optional.empty();
                }

                //runtime callback
                Optional
                    .ofNullable(runtime.onRunning(run, runnable))
                    .ifPresent(status -> run.setStatus(MapUtils.mergeMultipleMaps(run.getStatus(), status.toMap())));

                return Optional.empty();
            });
    }

    protected Transition.Builder<String, String, Run> loopRunning() {
        //(LOOP)->RUNNING
        return new Transition.Builder<String, String, Run>()
            .event(RunEvent.LOOP.name())
            .nextState(RunState.RUNNING.name())
            .<R, R>withInternalLogic((currentState, nextState, event, run, runnable) -> {
                RunSpecAccessor specAccessor = RunSpecAccessor.with(run.getSpec());
                if (specAccessor.isLocalExecution()) {
                    return Optional.empty();
                }

                //runtime callback
                Optional
                    .ofNullable(runtime.onRunning(run, runnable))
                    .ifPresent(status -> run.setStatus(MapUtils.mergeMultipleMaps(run.getStatus(), status.toMap())));

                return Optional.empty();
            });
    }

    protected Transition.Builder<String, String, Run> toCompleted() {
        //(COMPLETE)->COMPLETED
        return new Transition.Builder<String, String, Run>()
            .event(RunEvent.COMPLETE.name())
            .nextState(RunState.COMPLETED.name())
            .<R, R>withInternalLogic((currentState, nextState, event, run, runnable) -> {
                RunSpecAccessor specAccessor = RunSpecAccessor.with(run.getSpec());
                if (specAccessor.isLocalExecution()) {
                    return Optional.empty();
                }

                //runtime callback
                Optional
                    .ofNullable(runtime.onComplete(run, runnable))
                    .ifPresent(status -> run.setStatus(MapUtils.mergeMultipleMaps(run.getStatus(), status.toMap())));

                //final state, cleanup
                if (runnable != null) {
                    return Optional.ofNullable(runtime.delete(run));
                }

                return Optional.empty();
            });
    }

    protected Transition.Builder<String, String, Run> toStop() {
        //(STOP)->STOP
        return new Transition.Builder<String, String, Run>()
            .event(RunEvent.STOP.name())
            .nextState(RunState.STOP.name())
            .<R, R>withInternalLogic((currentState, nextState, event, run, runnable) -> {
                RunSpecAccessor specAccessor = RunSpecAccessor.with(run.getSpec());
                if (specAccessor.isLocalExecution()) {
                    return Optional.empty();
                }

                //stop via runtime
                return Optional.ofNullable(runtime.stop(run));
            });
    }

    protected Transition.Builder<String, String, Run> toStopped() {
        //(STOP)->STOPPED
        return new Transition.Builder<String, String, Run>()
            .event(RunEvent.STOP.name())
            .nextState(RunState.STOPPED.name())
            .<R, R>withInternalLogic((currentState, nextState, event, run, runnable) -> {
                RunSpecAccessor specAccessor = RunSpecAccessor.with(run.getSpec());
                if (specAccessor.isLocalExecution()) {
                    return Optional.empty();
                }

                //runtime callback
                Optional
                    .ofNullable(runtime.onStopped(run, runnable))
                    .ifPresent(status -> run.setStatus(MapUtils.mergeMultipleMaps(run.getStatus(), status.toMap())));

                return Optional.empty();
            });
    }

    protected Transition.Builder<String, String, Run> toResume() {
        //(RESUME)->RESUME
        return new Transition.Builder<String, String, Run>()
            .event(RunEvent.RESUME.name())
            .nextState(RunState.RESUME.name())
            .<R, R>withInternalLogic((currentState, nextState, event, run, runnable) -> {
                RunSpecAccessor specAccessor = RunSpecAccessor.with(run.getSpec());
                if (specAccessor.isLocalExecution()) {
                    return Optional.empty();
                }

                //resume via runtime
                return Optional.ofNullable(runtime.resume(run));
            });
    }
}
