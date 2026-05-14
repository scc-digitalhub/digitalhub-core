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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ResolvableTypeProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.TransientSecurityContext;
import org.springframework.stereotype.Component;

/**
 * Single consumer for the entityEventQueueChannel.
 * Routes each EntityEvent to the correct AbstractEntityOperationsListener by
 * matching
 * the concrete entity class carried in the event payload to the entity class
 * each listener was registered for.
 */
@Component
@Slf4j
public class EntityOperationsDispatcher {

    private final Map<Class<?>, EntityOperationsListener<?>> listeners = new HashMap<>();
    protected MessageChannel entityOperationsChannel;

    public EntityOperationsDispatcher(List<EntityOperationsListener<?>> allListeners) {
        for (EntityOperationsListener<?> listener : allListeners) {
            if (listener instanceof ResolvableTypeProvider resolvableProvider) {
                Class<?> entityClass = resolvableProvider.getResolvableType().resolve();
                if (
                    entityClass != null &&
                    BaseDTO.class.isAssignableFrom(entityClass) &&
                    StatusDTO.class.isAssignableFrom(entityClass)
                ) {
                    log.debug(
                        "registering entity operations listener {} for entity class {}",
                        listener.getClass().getSimpleName(),
                        entityClass.getSimpleName()
                    );
                    listeners.put(entityClass, listener);
                }
            }
        }
    }

    @Autowired(required = false)
    @Qualifier("entityOperationsQueueChannel")
    public void setEntityOperationsChannel(MessageChannel entityOperationsChannel) {
        this.entityOperationsChannel = entityOperationsChannel;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void handle(Message<EntityOperation<?>> message) {
        if (message == null) {
            return;
        }

        EntityOperation<?> event = message.getPayload();
        Class<?> clazz = event.getResolvableType().getGeneric(0).resolve();
        if (event.getDto() == null) {
            return;
        }

        EntityOperationsListener<BaseDTO> listener = (EntityOperationsListener<BaseDTO>) listeners.get(clazz);
        if (listener == null) {
            log.warn("no listener registered for entity class {}", clazz.getName());
            return;
        }

        log.debug("dispatch entity event {} for {}", event.getAction(), clazz.getSimpleName());
        String user = event.getDto().getUser();
        if (user != null) {
            // TODO restore user roles/context?
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user,
                null,
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER"))
            );
            SecurityContext ctx = new TransientSecurityContext(auth);
            SecurityContextHolder.setContext(ctx);
        }
        try {
            listener.receive((EntityOperation<BaseDTO>) message.getPayload());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
