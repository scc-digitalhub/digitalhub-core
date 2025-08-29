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

package it.smartcommunitylabdhub.core.triggers.lifecycle;

import it.smartcommunitylabdhub.authorization.UserAuthenticationManager;
import it.smartcommunitylabdhub.authorization.UserAuthenticationManagerBuilder;
import it.smartcommunitylabdhub.authorization.providers.NoOpAuthenticationProvider;
import it.smartcommunitylabdhub.commons.models.trigger.Trigger;
import it.smartcommunitylabdhub.commons.services.TriggerManager;
import it.smartcommunitylabdhub.triggers.infrastructure.TriggerRun;
import it.smartcommunitylabdhub.triggers.lifecycle.TriggerExecutionEvent;
import it.smartcommunitylabdhub.triggers.lifecycle.TriggerJob;
import java.util.Collections;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.TransientSecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@Slf4j
public class TriggerListener {

    private final TriggerManager triggerService;
    private final KindAwareTriggerLifecycleManager triggerManager;
    private final ThreadPoolTaskExecutor executor;

    private UserAuthenticationManager authenticationManager;

    @Autowired
    public void setAuthenticationManagerBuilder(UserAuthenticationManagerBuilder authenticationManagerBuilder) {
        this.authenticationManager = authenticationManagerBuilder.build(new NoOpAuthenticationProvider());
    }

    public TriggerListener(TriggerManager triggerService, KindAwareTriggerLifecycleManager triggerManager) {
        Assert.notNull(triggerManager, "trigger manager is required");
        Assert.notNull(triggerService, "trigger service is required");
        this.triggerService = triggerService;
        this.triggerManager = triggerManager;

        //create local executor pool to delegate+inject user context in async tasks
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setThreadNamePrefix("triggerlm-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
    }

    /*
     * Delegating security context
     */
    private void wrap(Trigger trigger, TriggerRun<TriggerJob> run, BiConsumer<Trigger, TriggerRun<TriggerJob>> lambda) {
        String user = run.getUser();

        log.trace("wrap trigger callback for user {}", String.valueOf(user));
        if (user != null) {
            //wrap in a security context

            UsernamePasswordAuthenticationToken userAuth = new UsernamePasswordAuthenticationToken(
                user,
                null,
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER"))
            );

            // restore user roles/context
            Authentication auth = userAuth;
            if (authenticationManager != null) {
                //process to get full credentials
                auth = authenticationManager.process(userAuth);
            }

            Runnable wrapped = DelegatingSecurityContextRunnable.create(
                () -> lambda.accept(trigger, run),
                new TransientSecurityContext(auth)
            );
            executor.execute(wrapped);
        } else {
            //run as system
            executor.execute(() -> lambda.accept(trigger, run));
        }
    }

    @Async
    @EventListener
    public void receive(TriggerExecutionEvent<TriggerJob> event) {
        if (event.getEvent() == null) {
            return;
        }

        log.debug("receive event {} for {}", event.getEvent(), event.getId());
        if (log.isTraceEnabled()) {
            log.trace("event: {}", event);
        }

        String id = event.getId();

        if (event.getEvent() == null) {
            log.error("Missing event for id {}", id);
            return;
        }

        //load trigger from db
        Trigger trigger = triggerService.findTrigger(event.getId());
        if (trigger == null) {
            log.error("Trigger with id {} not found", id);
            return;
        }

        // handle with manager
        wrap(trigger, event.getRun(), (tr, r) -> triggerManager.perform(tr, event.getEvent().name(), r));
    }
}
