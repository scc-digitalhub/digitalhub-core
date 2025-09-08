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
import it.smartcommunitylabdhub.commons.infrastructure.Runtime;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.lifecycle.BaseLifecycleManager;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Map;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;

@Slf4j
public class RunLifecycleManager<S extends RunBaseSpec, Z extends RunBaseStatus, R extends RunRunnable>
    extends BaseLifecycleManager<Run, RunState, RunEvent> {

    public RunLifecycleManager(Runtime<S, Z, R> runtime) {
        this(new RunFsmFactory<>(runtime));
    }

    public RunLifecycleManager(RunFsmFactory<S, Z, R> fsmFactory) {
        //fix internal types because we have a different signature
        //this will shadow superclass generic visibility
        super(Run.class, RunState.class, RunEvent.class);
        //set fsm factory
        this.setFsmFactory(fsmFactory);
    }

    @Override
    public <I, RT> Run handle(@NotNull Run dto, String nextStateValue, I input, BiConsumer<Run, RT> effect) {
        if (effect == null) {
            //by default we expect a runnable as optional output from runtimes
            effect =
                (run, runnable) -> {
                    if (runnable != null && runnable instanceof RunRunnable) {
                        //patch user from context if available
                        if (SecurityContextHolder.getContext().getAuthentication() != null) {
                            ((RunRunnable) runnable).setUser(
                                    SecurityContextHolder.getContext().getAuthentication().getName()
                                );
                        }

                        //publish to dispatcher
                        this.eventPublisher.publishEvent(runnable);
                    }
                };
        }

        return super.handle(dto, nextStateValue, input, effect);
    }

    @Override
    public <I, RT> Run perform(@NotNull Run dto, @NotNull String event, I input, BiConsumer<Run, RT> effect) {
        if (effect == null) {
            //by default we expect a runnable as optional output from runtimes
            effect =
                (run, runnable) -> {
                    if (runnable != null && runnable instanceof RunRunnable) {
                        //patch user from context if available
                        if (SecurityContextHolder.getContext().getAuthentication() != null) {
                            ((RunRunnable) runnable).setUser(
                                    SecurityContextHolder.getContext().getAuthentication().getName()
                                );
                        }

                        //publish to dispatcher, we will receive a callback
                        this.eventPublisher.publishEvent(runnable);
                    } else if (runnable == null && RunEvent.DELETE.name().equals(event)) {
                        StatusFieldAccessor status = StatusFieldAccessor.with(run.getStatus());
                        if (RunState.DELETING.name().equals(status.getState())) {
                            //short circuit DELETING for no-ops to DELETED
                            //this will let manager DELETE the entity
                            Map<String, Serializable> baseStatus = Map.of("state", RunState.DELETED.name());
                            run.setStatus(MapUtils.mergeMultipleMaps(run.getStatus(), baseStatus));
                        }
                    }
                };
        }

        return super.perform(dto, event, input, effect);
    }
}
