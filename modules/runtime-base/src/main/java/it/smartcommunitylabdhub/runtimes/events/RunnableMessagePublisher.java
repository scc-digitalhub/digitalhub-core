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

package it.smartcommunitylabdhub.runtimes.events;

import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;

@Slf4j
public class RunnableMessagePublisher {

    private final MessageChannel channel;

    public RunnableMessagePublisher(MessageChannel runnableMessageChannel) {
        this.channel = runnableMessageChannel;
    }

    public void publish(RunRunnable runnable) {
        if (runnable == null) {
            return;
        }

        log.debug("publish message for runnable {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("publishing runnable event for runnable {}", runnable.getId());
        }

        channel.send(MessageBuilder.withPayload(runnable).build());
    }
}
