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

package it.smartcommunitylabdhub.components.cloud;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.events.EntityAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "event-queue.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class RabbitMQBroadcaster {

    private static final ObjectMapper mapper = JacksonMapper.CBOR_OBJECT_MAPPER;

    private final RabbitTemplate rabbitTemplate;

    public RabbitMQBroadcaster(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Async
    @EventListener
    public void handleEntitySavedEvent(CloudEntityEvent<?> event) {
        // Broadcast event on rabbit amqp
        try {
            if (event.getAction() != EntityAction.DELETE) {
                rabbitTemplate.convertAndSend("entityTopic", "entityRoutingKey", mapper.writeValueAsString(event));
            }
        } catch (JsonProcessingException e) {
            log.error("Error serializing cloud event for rabbit", e.getMessage());
        }
    }
}
