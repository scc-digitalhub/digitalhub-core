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
import it.smartcommunitylabdhub.commons.lifecycle.LifecycleEvents;
import it.smartcommunitylabdhub.commons.lifecycle.LifecycleState;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.fsm.FsmState;
import it.smartcommunitylabdhub.fsm.Transition;
import it.smartcommunitylabdhub.runtimes.lifecycle.RunEvent;
import it.smartcommunitylabdhub.runtimes.lifecycle.RunState;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

@Slf4j
public class BaseRunState<
    S extends Enum<S> & LifecycleState<Run>,
    E extends Enum<E> & LifecycleEvents<Run>,
    X extends RunBaseSpec,
    Z extends RunBaseStatus,
    R extends RunRunnable
>
    implements FsmState.Builder<S, E, Run> {

    protected final Class<S> stateClass;
    protected final Class<E> eventsClass;

    protected final S state;
    protected final Runtime<X, Z, R> runtime;

    protected List<Transition<S, E, Run>> txs;

    @SuppressWarnings("unchecked")
    public BaseRunState(S state, Runtime<X, Z, R> runtime) {
        Assert.notNull(state, "state is required");
        Assert.notNull(runtime, "runtime is required");

        this.state = state;
        this.runtime = runtime;

        // resolve generics type via subclass trick
        Type ts = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.stateClass = (Class<S>) ts;
        Type te = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[1];
        this.eventsClass = (Class<E>) te;
    }

    public FsmState<S, E, Run> build() {
        return new FsmState<>(state, txs);
    }

    //TODO evaluate splitting to factory classes
    protected Transition.Builder<S, E, Run> toError() {
        //(ERROR)->ERROR
        return new Transition.Builder<S, E, Run>()
            .event(Enum.valueOf(eventsClass, RunEvent.ERROR.name()))
            .nextState(Enum.valueOf(stateClass, RunState.ERROR.name()))
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

    protected Transition.Builder<S, E, Run> toDeleting() {
        //(DELETE)->DELETING
        return new Transition.Builder<S, E, Run>()
            .event(Enum.valueOf(eventsClass, RunEvent.DELETE.name()))
            .nextState(Enum.valueOf(stateClass, RunState.DELETING.name()))
            .<R, R>withInternalLogic((currentState, nextState, event, run, runnable) -> {
                RunSpecAccessor specAccessor = RunSpecAccessor.with(run.getSpec());
                if (specAccessor.isLocalExecution()) {
                    return Optional.empty();
                }

                //delete via runtime
                return Optional.ofNullable(runtime.delete(run));
            });
    }

    protected Transition.Builder<S, E, Run> toDelete() {
        //(DELETE)->DELETED
        return new Transition.Builder<S, E, Run>()
            .event(Enum.valueOf(eventsClass, RunEvent.DELETE.name()))
            .nextState(Enum.valueOf(stateClass, RunState.DELETED.name()))
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

    protected Transition.Builder<S, E, Run> toReady() {
        //(RUN)->READY
        return new Transition.Builder<S, E, Run>()
            .event(Enum.valueOf(eventsClass, RunEvent.RUN.name()))
            .nextState(Enum.valueOf(stateClass, RunState.READY.name()))
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

    protected Transition.Builder<S, E, Run> toPending() {
        //(EXECUTE)->RUNNING
        return new Transition.Builder<S, E, Run>()
            .event(Enum.valueOf(eventsClass, RunEvent.SCHEDULE.name()))
            .nextState(Enum.valueOf(stateClass, RunState.PENDING.name()))
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

    protected Transition.Builder<S, E, Run> toRunning() {
        //(EXECUTE)->RUNNING
        return new Transition.Builder<S, E, Run>()
            .event(Enum.valueOf(eventsClass, RunEvent.EXECUTE.name()))
            .nextState(Enum.valueOf(stateClass, RunState.RUNNING.name()))
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

    protected Transition.Builder<S, E, Run> loopRunning() {
        //(LOOP)->RUNNING
        return new Transition.Builder<S, E, Run>()
            .event(Enum.valueOf(eventsClass, RunEvent.LOOP.name()))
            .nextState(Enum.valueOf(stateClass, RunState.RUNNING.name()))
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

    protected Transition.Builder<S, E, Run> toCompleted() {
        //(COMPLETE)->COMPLETED
        return new Transition.Builder<S, E, Run>()
            .event(Enum.valueOf(eventsClass, RunEvent.COMPLETE.name()))
            .nextState(Enum.valueOf(stateClass, RunState.COMPLETED.name()))
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

    protected Transition.Builder<S, E, Run> toStop() {
        //(STOP)->STOP
        return new Transition.Builder<S, E, Run>()
            .event(Enum.valueOf(eventsClass, RunEvent.STOP.name()))
            .nextState(Enum.valueOf(stateClass, RunState.STOP.name()))
            .<R, R>withInternalLogic((currentState, nextState, event, run, runnable) -> {
                RunSpecAccessor specAccessor = RunSpecAccessor.with(run.getSpec());
                if (specAccessor.isLocalExecution()) {
                    return Optional.empty();
                }

                //stop via runtime
                return Optional.ofNullable(runtime.stop(run));
            });
    }

    protected Transition.Builder<S, E, Run> toStopped() {
        //(STOP)->STOPPED
        return new Transition.Builder<S, E, Run>()
            .event(Enum.valueOf(eventsClass, RunEvent.STOP.name()))
            .nextState(Enum.valueOf(stateClass, RunState.STOPPED.name()))
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

    protected Transition.Builder<S, E, Run> toResume() {
        //(RESUME)->RESUME
        return new Transition.Builder<S, E, Run>()
            .event(Enum.valueOf(eventsClass, RunEvent.RESUME.name()))
            .nextState(Enum.valueOf(stateClass, RunState.RESUME.name()))
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
