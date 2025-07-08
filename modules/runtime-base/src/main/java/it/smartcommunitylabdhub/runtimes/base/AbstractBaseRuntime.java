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
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.infrastructure.Runtime;
import it.smartcommunitylabdhub.commons.models.base.ExecutableBaseSpec;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
public abstract class AbstractBaseRuntime<
    F extends ExecutableBaseSpec, S extends RunBaseSpec, Z extends RunBaseStatus, R extends RunRunnable
>
    implements Runtime<F, S, Z, R> {

    private final String kind;

    protected Collection<RunnableStore<R>> stores = Collections.emptyList();

    protected AbstractBaseRuntime(String kind) {
        Assert.hasText(kind, "run kind must be specified");
        this.kind = kind;
    }

    @Autowired(required = false)
    public void setStores(Collection<RunnableStore<? extends RunRunnable>> stores) {
        this.stores =
            stores
                .stream()
                .filter(s -> {
                    try {
                        R r = (R) s.getResolvableType().resolve().getDeclaredConstructor().newInstance();
                        return r != null;
                    } catch (
                        NullPointerException
                        | ClassCastException
                        | InstantiationException
                        | IllegalAccessException
                        | IllegalArgumentException
                        | InvocationTargetException
                        | NoSuchMethodException
                        | SecurityException e
                    ) {
                        return false;
                    }
                })
                .map(s -> (RunnableStore<R>) s)
                .collect(Collectors.toList());

        log.debug("registered stores for {}: {}", getClass().getName(), this.stores);
    }

    @Override
    public R stop(@NotNull Run run) {
        //check run kind
        if (!kind.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), kind)
            );
        }

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        if (!StringUtils.hasText(runAccessor.getTask())) {
            throw new IllegalArgumentException("Run task invalid");
        }

        String task = runAccessor.getTask();

        //iterate over stores to find first matching runnable and stop
        Optional<R> runnable = stores
            .stream()
            .map(s -> {
                try {
                    return s.find(run.getId());
                } catch (StoreException e) {
                    return null;
                }
            })
            .filter(f -> f != null)
            //sanity check that task matches
            .filter(r -> task.equals(r.getTask()))
            .findFirst();

        if (runnable.isPresent()) {
            R runRunnable = runnable.get();
            runRunnable.setState(State.STOP.name());
            runRunnable.setMessage("stopping runnable " + runRunnable.getId());
            return runRunnable;
        }

        log.warn("Error stopping run {}", run.getId());
        throw new NoSuchEntityException("Error stopping run");
    }

    @Override
    public R resume(@NotNull Run run) {
        //check run kind
        if (!kind.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), kind)
            );
        }

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        if (!StringUtils.hasText(runAccessor.getTask())) {
            throw new IllegalArgumentException("Run task invalid");
        }

        String task = runAccessor.getTask();

        //iterate over stores to find first matching runnable and resume
        Optional<R> runnable = stores
            .stream()
            .map(s -> {
                try {
                    return s.find(run.getId());
                } catch (StoreException e) {
                    return null;
                }
            })
            .filter(f -> f != null)
            //sanity check that task matches
            .filter(r -> task.equals(r.getTask()))
            .findFirst();

        if (runnable.isPresent()) {
            R runRunnable = runnable.get();
            runRunnable.setState(State.RESUME.name());
            runRunnable.setMessage("resuming runnable " + runRunnable.getId());
            return runRunnable;
        }

        log.warn("Error resuming run {}", run.getId());
        throw new NoSuchEntityException("Error stopping run");
    }

    @Override
    @Nullable
    public R delete(@NotNull Run run) {
        //check run kind
        if (!kind.equals(run.getKind())) {
            throw new IllegalArgumentException(
                "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), kind)
            );
        }

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        if (!StringUtils.hasText(runAccessor.getTask())) {
            throw new IllegalArgumentException("Run task invalid");
        }

        String task = runAccessor.getTask();

        //iterate over stores to find first matching runnable and delete
        Optional<R> runnable = stores
            .stream()
            .map(s -> {
                try {
                    return s.find(run.getId());
                } catch (StoreException e) {
                    return null;
                }
            })
            .filter(f -> f != null)
            //sanity check that task matches
            .filter(r -> task.equals(r.getTask()))
            .findFirst();

        if (runnable.isPresent()) {
            R runRunnable = runnable.get();
            runRunnable.setState(State.DELETING.name());
            runRunnable.setMessage("deleting runnable " + runRunnable.getId());
            return runRunnable;
        }

        log.debug("Error deleting run {}", run.getId());
        return null;
    }

    @Override
    public Z onDeleted(@NotNull Run run, @Nullable RunRunnable runnable) {
        if (runnable != null) {
            //check run kind
            if (!kind.equals(run.getKind())) {
                throw new IllegalArgumentException(
                    "Run kind {} unsupported, expecting {}".formatted(String.valueOf(run.getKind()), kind)
                );
            }

            //check run id matches runnable
            if (run.getId() == null || !run.getId().equals(runnable.getId())) {
                throw new IllegalArgumentException("Run id mismatch");
            }

            // Create string run accessor from task
            RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

            if (!StringUtils.hasText(runAccessor.getTask())) {
                throw new IllegalArgumentException("Run task invalid");
            }

            String task = runAccessor.getTask();

            //iterate over stores to find first matching runnable and remove
            stores
                .stream()
                .filter(s -> {
                    try {
                        R r = s.find(runnable.getId());
                        return r != null && task.equals(r.getTask());
                    } catch (StoreException e) {
                        log.warn("Error with store", e);
                        return false;
                    }
                })
                .findFirst()
                .ifPresent(store -> {
                    try {
                        store.remove(runnable.getId());
                    } catch (StoreException e) {
                        log.error("Error deleting runnable", e);
                    }
                });
        }
        return null;
    }
}
