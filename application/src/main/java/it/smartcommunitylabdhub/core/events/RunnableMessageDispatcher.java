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

import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.runtimes.events.RunnableListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.ResolvableTypeProvider;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Single consumer for the runnableMessageQueue.
 * Routes each RunnableEvent to the correct AbstractRunnableListener by matching
 * the concrete runnable class carried in the event payload to the runnable class
 * each listener was registered for.
 */
@Component
@Slf4j
public class RunnableMessageDispatcher {

    private final Map<Class<?>, RunnableListener<?>> listeners = new HashMap<>();

    public RunnableMessageDispatcher(List<RunnableListener<?>> allListeners) {
        for (RunnableListener<? extends RunRunnable> listener : allListeners) {
            if (listener instanceof ResolvableTypeProvider resolvableProvider) {
                Class<?> runnableClass = resolvableProvider.getResolvableType().resolve();
                if (runnableClass != null) {
                    log.debug(
                        "registering runnable event listener {} for runnable class {}",
                        listener.getClass().getSimpleName(),
                        runnableClass.getSimpleName()
                    );
                    listeners.put(runnableClass, listener);
                }
            }
        }
    }

    @Async
    @EventListener
    public void listen(RunRunnable runnable) {
        if (runnable == null) {
            return;
        }

        receive(MessageBuilder.withPayload(runnable).build());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void receive(Message<? extends RunRunnable> message) {
        if (message == null) {
            return;
        }

        RunRunnable runnable = message.getPayload();
        if (runnable == null) {
            return;
        }
        RunnableListener listener = listeners.get(runnable.getClass());
        if (listener == null) {
            log.warn("no listener registered for runnable class {}", runnable.getClass().getName());
            return;
        }

        log.debug("dispatch runnable event for {}", runnable.getClass().getSimpleName());

        listener.listen(runnable);
    }
}
