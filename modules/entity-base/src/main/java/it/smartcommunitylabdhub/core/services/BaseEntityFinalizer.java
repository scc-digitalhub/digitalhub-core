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

package it.smartcommunitylabdhub.core.services;

import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.events.EntityOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Base implementation of EntityFinalizer with no-op finalize method, to be extended by specific finalizers when needed.
 * Exposes operations channel to let finalizers asyncronously delete related entities, if needed.
 */

@Slf4j
public abstract class BaseEntityFinalizer<D extends BaseDTO> implements EntityFinalizer<D> {

    private ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    protected void dispatch(EntityOperation<?> operation) {
        if (operation.getDto() == null) {
            return;
        }

        Class<?> clazz = operation.getResolvableType().getGeneric(0).resolve();
        log.debug("dispatching operation {} for {}:{}", operation.getAction(), clazz, operation.getDto().getId());

        if (eventPublisher != null) {
            if (log.isTraceEnabled()) {
                log.trace("dto: {}", String.valueOf(operation.getDto()));
            }

            eventPublisher.publishEvent(operation);
        }
    }
}
