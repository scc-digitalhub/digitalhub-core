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

package it.smartcommunitylabdhub.trigger.lifecycle;

import it.smartcommunitylabdhub.commons.accessors.fields.KeyAccessor;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.ActuatorComponent;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.infrastructure.Actuator;
import it.smartcommunitylabdhub.commons.infrastructure.TriggerRun;
import it.smartcommunitylabdhub.commons.models.trigger.Trigger;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerBaseStatus;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerJob;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerRunBaseStatus;
import it.smartcommunitylabdhub.trigger.lifecycle.models.LifecycleStates;
import it.smartcommunitylabdhub.trigger.lifecycle.models.LifecycleTriggerJob;
import it.smartcommunitylabdhub.trigger.lifecycle.models.LifecycleTriggerSpec;
import it.smartcommunitylabdhub.trigger.lifecycle.store.TriggerJobStore;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
@ActuatorComponent(actuator = LifecycleActuator.ACTUATOR)
public class LifecycleActuator
    implements Actuator<LifecycleTriggerSpec, TriggerBaseStatus, TriggerRunBaseStatus>, InitializingBean {

    public static final String ACTUATOR = "lifecycle";

    private TriggerJobStore<LifecycleTriggerJob> store;

    @Autowired
    public void setStore(TriggerJobStore<LifecycleTriggerJob> store) {
        this.store = store;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(store, "store is required");
    }

    @Override
    public TriggerBaseStatus run(@NotNull Trigger trigger) {
        log.debug("create job for {}", trigger.getId());

        //build job
        LifecycleTriggerSpec spec = LifecycleTriggerSpec.from(trigger.getSpec());

        //validate states
        Set<LifecycleStates> states = spec.getStates() == null ? Set.of() : Set.copyOf(spec.getStates());

        //rebuild ant-style key
        KeyAccessor k = KeyAccessor.with(spec.getKey());

        //validate key
        if (!trigger.getProject().equals(k.getProject())) {
            throw new IllegalArgumentException("project in key does not match trigger project");
        }

        StringBuilder key = new StringBuilder("store://" + k.getProject());
        key.append(StringUtils.hasText(k.getType()) ? "/" + k.getType() : "/*");
        key.append(StringUtils.hasText(k.getKind()) ? "/" + k.getKind() : "/*");
        key.append(StringUtils.hasText(k.getName()) ? "/" + k.getName() : "/*");
        key.append(StringUtils.hasText(k.getId()) ? ":" + k.getId() : ":*");

        LifecycleTriggerJob job = LifecycleTriggerJob
            .builder()
            .id(trigger.getId())
            .user(trigger.getUser())
            .task(spec.getTask())
            .project(trigger.getProject())
            //spec
            .key(key.toString())
            .states(states.stream().map(LifecycleStates::name).collect(Collectors.toList()))
            .build();

        //store job
        try {
            store.store(trigger.getId(), job);
        } catch (StoreException e) {
            log.error("error storing job in store: {}", e.getMessage());
            throw new CoreRuntimeException("error storing job in store", e);
        }

        TriggerBaseStatus baseStatus = TriggerBaseStatus.with(trigger.getStatus());
        return baseStatus;
    }

    @Override
    public TriggerBaseStatus stop(@NotNull Trigger trigger) {
        log.debug("delete job for {}", trigger.getId());

        try {
            //remove job from store
            store.remove(trigger.getId());
        } catch (StoreException e) {
            log.error("error removing job from store: {}", e.getMessage());
            throw new CoreRuntimeException("error removing job from store", e);
        }

        TriggerBaseStatus baseStatus = TriggerBaseStatus.with(trigger.getStatus());
        return baseStatus;
    }

    @Override
    public TriggerRunBaseStatus onFire(@NotNull Trigger trigger, TriggerRun<? extends TriggerJob> run) {
        //nothing to do
        return null;
    }
}
