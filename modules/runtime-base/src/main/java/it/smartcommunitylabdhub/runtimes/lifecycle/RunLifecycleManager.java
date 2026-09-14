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

package it.smartcommunitylabdhub.runtimes.lifecycle;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.core.lifecycle.BaseLifecycleManager;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runs.lifecycle.RunEvent;
import it.smartcommunitylabdhub.runs.lifecycle.RunState;
import it.smartcommunitylabdhub.runs.specs.RunBaseSpec;
import it.smartcommunitylabdhub.runs.specs.RunBaseStatus;
import it.smartcommunitylabdhub.runtimes.Runtime;
import it.smartcommunitylabdhub.runtimes.events.RunnableMessagePublisher;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

@Slf4j
public class RunLifecycleManager<
    S extends RunBaseSpec,
    Z extends RunBaseStatus,
    R extends RunRunnable
> extends BaseLifecycleManager<Run> {

    protected RunnableMessagePublisher runnablePublisher;

    public RunLifecycleManager(Runtime<S, Z, R> runtime) {
        this(new RunFsmFactory<>(runtime));
    }

    public RunLifecycleManager(RunFsmFactory<S, Z, R> fsmFactory) {
        //fix internal types because we have a different signature
        //this will shadow superclass generic visibility
        super(Run.class);
        //set fsm factory
        this.setFsmFactory(fsmFactory);
    }

    @Autowired(required = false)
    public void setRunnablePublisher(RunnableMessagePublisher runnablePublisher) {
        this.runnablePublisher = runnablePublisher;
    }

    @Override
    public <I extends Serializable, RT extends Serializable> Run handle(
        @NotNull Run dto,
        String nextStateValue,
        I input,
        BiConsumer<Run, RT> effect
    ) {
        //by default we expect a runnable as optional output from runtimes
        BiConsumer<Run, RT> callback = (run, runnable) -> {
            //support multiple returns from runtimes, we will publish all runnables returned by the runtime
            if (runnable instanceof Collection) {
                ((Collection<?>) runnable).stream()
                    .filter(RunRunnable.class::isInstance)
                    .map(r -> (RunRunnable) r)
                    .forEach(this::dispatch);
            } else if (runnable instanceof RunRunnable runRunnable) {
                dispatch(runRunnable);
            }

            //if provided, execute the effect
            if (effect != null) {
                effect.accept(run, runnable);
            }
        };

        return super.handle(dto, nextStateValue, input, callback);
    }

    @Override
    public <I extends Serializable, RT extends Serializable> Run perform(
        @NotNull Run dto,
        @NotNull String event,
        I input,
        BiConsumer<Run, RT> effect
    ) {
        //by default we expect a runnable as optional output from runtimes
        BiConsumer<Run, RT> callback = (run, runnable) -> {
            if (runnable != null) {
                if (runnable instanceof Collection) {
                    ((Collection<?>) runnable).stream()
                        .filter(RunRunnable.class::isInstance)
                        .map(r -> (RunRunnable) r)
                        .forEach(this::dispatch);
                } else if (runnable instanceof RunRunnable runRunnable) {
                    dispatch(runRunnable);
                }
            } else if (runnable == null && RunEvent.DELETE.name().equals(event)) {
                StatusFieldAccessor status = StatusFieldAccessor.with(run.getStatus());
                if (RunState.DELETING.name().equals(status.getState())) {
                    //short circuit DELETING for no-ops to DELETED
                    //this will let manager DELETE the entity
                    Map<String, Serializable> baseStatus = Map.of("state", RunState.DELETED.name());
                    run.setStatus(MapUtils.mergeMultipleMaps(run.getStatus(), baseStatus));
                }
            }

            //if provided, execute the effect
            if (effect != null) {
                effect.accept(run, runnable);
            }
        };

        return super.perform(dto, event, input, callback);
    }

    private void dispatch(RunRunnable runnable) {
        if (runnable != null) {
            //patch user from context if available
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                runnable.setUser(SecurityContextHolder.getContext().getAuthentication().getName());
            }

            //publish to dispatcher, we will receive a callback
            if (runnablePublisher != null) {
                if (log.isTraceEnabled()) {
                    log.trace("publishing runnable {} as message to dispatcher", runnable);
                }
                runnablePublisher.publish(runnable);
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("publishing runnable {} as event to listener", runnable);
                }
                this.eventPublisher.publishEvent(runnable);
            }
        }
    }
}
