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
import it.smartcommunitylabdhub.core.persistence.BaseEntity;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.TransientSecurityContext;
import org.springframework.stereotype.Component;

/**
 * Single consumer for the entityEventQueueChannel.
 * Routes each EntityEvent to the correct AbstractEntityListener by matching
 * the concrete entity class carried in the event payload to the entity class
 * each listener was registered for.
 */
@Component
@Slf4j
public class EntityEventDispatcher {

    private final Map<
        Class<? extends BaseEntity>,
        AbstractEntityListener<? extends BaseEntity, ? extends BaseDTO>
    > listeners = new HashMap<>();

    public EntityEventDispatcher(List<AbstractEntityListener<? extends BaseEntity, ? extends BaseDTO>> allListeners) {
        for (AbstractEntityListener<? extends BaseEntity, ? extends BaseDTO> listener : allListeners) {
            @SuppressWarnings("unchecked")
            Class<? extends BaseEntity> entityClass = (Class<? extends BaseEntity>) listener
                .getResolvableType()
                .resolve();
            if (entityClass != null) {
                log.debug(
                    "registering entity event listener {} for entity class {}",
                    listener.getClass().getSimpleName(),
                    entityClass.getSimpleName()
                );
                listeners.put(entityClass, listener);
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void handle(Message<EntityEvent<? extends BaseEntity>> message) {
        if (message == null) {
            return;
        }

        EntityEvent<? extends BaseEntity> event = message.getPayload();
        Class<? extends BaseEntity> clazz = (Class<? extends BaseEntity>) event
            .getResolvableType()
            .getGeneric(0)
            .resolve();
        if (event.getEntity() == null) {
            return;
        }

        AbstractEntityListener listener = listeners.get(clazz);
        if (listener == null) {
            log.warn("no listener registered for entity class {}", clazz.getName());
            return;
        }

        log.debug("dispatch entity event {} for {}", event.getAction(), clazz.getSimpleName());
        String user = event.getEntity().getCreatedBy();
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
            listener.handle(message);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
