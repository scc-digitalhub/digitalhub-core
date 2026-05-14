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

package it.smartcommunitylabdhub.core.events;

import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.events.EntityOperation;
import it.smartcommunitylabdhub.lifecycle.LifecycleManager;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;

@Slf4j
public abstract class AbstractEntityOperationsListener<
    D extends BaseDTO & StatusDTO
> implements ResolvableTypeProvider {

    protected final Class<D> clazz;
    protected LifecycleManager<D> lifecycleManager;

    protected MessageChannel entityOperationsChannel;

    @Autowired(required = false)
    @Qualifier("entityOperationsQueueChannel")
    public void setEntityOperationsChannel(MessageChannel entityOperationsChannel) {
        this.entityOperationsChannel = entityOperationsChannel;
    }

    @SuppressWarnings("unchecked")
    protected AbstractEntityOperationsListener() {
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.clazz = (Class<D>) t;
    }

    @Autowired(required = false)
    public void setLifecycleManager(LifecycleManager<D> lifecycleManager) {
        this.lifecycleManager = lifecycleManager;
    }

    public ResolvableType getResolvableType() {
        return ResolvableType.forClass(clazz);
    }

    protected void dispatch(EntityOperation<D> operation) {
        log.debug("dispatch event for {} {}", clazz.getSimpleName(), operation.getAction());
        if (entityOperationsChannel != null) {
            entityOperationsChannel.send(MessageBuilder.withPayload(operation).build());
        } else {
            log.warn("entityEventChannel not wired, handling event inline for {}", clazz.getSimpleName());
            handle(operation);
        }
    }

    public void handle(Message<EntityOperation<D>> message) {
        if (message == null) {
            return;
        }

        EntityOperation<D> operation = message.getPayload();
        handle(operation);
    }

    protected void handle(EntityOperation<D> operation) {
        log.debug("receive event for {} {}", clazz.getSimpleName(), operation.getAction());

        D dto = operation.getDto();
        if (log.isTraceEnabled()) {
            log.trace("{}: {}", clazz.getSimpleName(), String.valueOf(dto));
        }

        if (dto == null) {
            return;
        }

        // handle delete via manager
        if (lifecycleManager != null) {
            // perform action via manager
            // TODO fix operation/event naming mismatch: operation is an event, but action
            // is a command, so we need to use the action name as the command to perform
            lifecycleManager.perform(dto, operation.getAction().name());
        }
    }
}
