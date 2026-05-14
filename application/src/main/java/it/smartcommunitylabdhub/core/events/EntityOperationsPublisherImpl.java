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

import it.smartcommunitylabdhub.events.EntityOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
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
public class EntityOperationsPublisherImpl implements EntityOperationsPublisher {

    protected MessageChannel entityOperationsChannel;

    @Autowired(required = false)
    @Qualifier("entityOperationsQueueChannel")
    public void setEntityOperationsChannel(MessageChannel entityOperationsChannel) {
        this.entityOperationsChannel = entityOperationsChannel;
    }

    @Override
    public void publish(EntityOperation<?> operation) {
        if (entityOperationsChannel != null) {
            entityOperationsChannel.send(MessageBuilder.withPayload(operation).build());
        } else {
            throw new UnsupportedOperationException("Entity operations channel is not configured");
        }
    }
}
