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

package it.smartcommunitylabdhub.core.runs.listeners;

import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.core.runs.lifecycle.KindAwareRunLifecycleManager;
import it.smartcommunitylabdhub.events.EntityAction;
import it.smartcommunitylabdhub.events.EntityOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class RunOperationsListener {

    private KindAwareRunLifecycleManager runManager;

    @Autowired
    public void setRunManager(KindAwareRunLifecycleManager runManager) {
        this.runManager = runManager;
    }

    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void receive(EntityOperation<Run> event) {
        log.debug("receive operation for {}", event.getAction());

        Run dto = event.getDto();
        if (log.isTraceEnabled()) {
            log.trace("run: {}", String.valueOf(dto));
        }

        if (EntityAction.DELETE == event.getAction()) {
            //handle delete via manager
            if (runManager != null) {
                //delete via manager
                runManager.perform(dto, "DELETE");
            }
        }
    }
}
