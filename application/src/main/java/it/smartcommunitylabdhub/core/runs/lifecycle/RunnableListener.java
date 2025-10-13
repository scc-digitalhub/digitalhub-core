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

package it.smartcommunitylabdhub.core.runs.lifecycle;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.services.RunManager;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.runtimes.events.RunnableChangedEvent;
import java.util.Collection;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.TransientSecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@Slf4j
public class RunnableListener {

    private final RunManager runService;
    private final KindAwareRunLifecycleManager runManager;
    private final ThreadPoolTaskExecutor executor;
    private Collection<RunnableStore<?>> stores = Collections.emptyList();

    public RunnableListener(RunManager runService, KindAwareRunLifecycleManager runManager) {
        Assert.notNull(runManager, "run manager is required");
        Assert.notNull(runService, "run service is required");
        this.runService = runService;
        this.runManager = runManager;

        //create local executor pool to delegate+inject user context in async tasks
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setThreadNamePrefix("runlm-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
    }

    @Autowired(required = false)
    public void setStores(Collection<RunnableStore<?>> stores) {
        this.stores = stores;
    }

    /*
     * Delegating security context
     */
    private void wrap(Run run, RunRunnable runnable, BiConsumer<Run, RunRunnable> lambda) {
        String user = runnable.getUser();

        log.trace("wrap run callback for user {}", String.valueOf(user));
        if (user != null) {
            //wrap in a security context
            //TODO restore user roles/context?
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user,
                null,
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER"))
            );
            Runnable wrapped = DelegatingSecurityContextRunnable.create(
                () -> lambda.accept(run, runnable),
                new TransientSecurityContext(auth)
            );
            executor.execute(wrapped);
        } else {
            //run as system
            executor.execute(() -> lambda.accept(run, runnable));
        }
    }

    @Async
    @EventListener
    public void receive(RunnableChangedEvent<RunRunnable> event) {
        if (event.getState() == null) {
            return;
        }

        log.debug("onChanged run with id {}: {}", event.getId(), event.getState());
        if (log.isTraceEnabled()) {
            log.trace("event: {}", event);
        }

        try {
            //read event
            String id = event.getId();
            String state = event.getState();

            // Use service to retrieve the run and check if state is changed
            Run run = runService.findRun(id);
            if (run == null) {
                log.warn("Run with id {} not found", id);
                //orphan run, remove from store
                if (stores != null) {
                    stores.forEach(store -> {
                        try {
                            RunRunnable rr = store.find(id);
                            if (rr != null) {
                                log.warn(
                                    "Remove orphaned runnable {} from store {}",
                                    id,
                                    store.getResolvableType().resolve().getName()
                                );
                                store.remove(id);
                            }
                        } catch (StoreException e) {
                            //ignore
                        }
                    });
                }
                return;
            }

            if (state == null) {
                log.error("State is null for run id {}", id);
                return;
            }

            // handle with manager
            // Note: we always handle the event, as it can be a progress update
            // even if the state is the same (e.g., RUNNING)
            // Moreover, some runtimes may not update the state properly
            // and we want to be sure to handle the event
            // Thus, we rely on the manager to handle idempotency and state checks
            // before applying any change
            // This also allows to handle events like STOPPED or DELETED
            // even if the state is not changed
            // Finally, we may want to log or track progress even if the state is the same
            // for monitoring purposes
            wrap(run, event.getRunnable(), (r, rb) -> runManager.handle(r, state, rb));
        } catch (Exception e) {
            log.error("Error handling runnable changed event: {}", e.getMessage(), e);
        }
    }
}
