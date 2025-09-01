/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * Copyright 2025 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.trigger.lifecycle;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.lifecycle.LifecycleEvent;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.relationships.RelationshipName;
import it.smartcommunitylabdhub.trigger.lifecycle.models.LifecycleTriggerJob;
import it.smartcommunitylabdhub.trigger.lifecycle.store.TriggerJobStore;
import it.smartcommunitylabdhub.triggers.infrastructure.TriggerRun;
import it.smartcommunitylabdhub.triggers.lifecycle.TriggerEvent;
import it.smartcommunitylabdhub.triggers.lifecycle.TriggerExecutionEvent;
import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;
import org.springframework.util.PathMatcher;

@Slf4j
@Component
public class LifecycleTriggerListener {

    private TriggerJobStore<LifecycleTriggerJob> store;
    private ApplicationEventPublisher applicationEventPublisher;
    private PathMatcher matcher = new AntPathMatcher();

    @Autowired
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Autowired(required = false)
    public void setStore(TriggerJobStore<LifecycleTriggerJob> store) {
        this.store = store;
    }

    public void setMatcher(PathMatcher matcher) {
        Assert.notNull(matcher, "matcher is required");
        this.matcher = matcher;
    }

    @Async
    @EventListener
    public void receive(LifecycleEvent<? extends BaseDTO> event) {
        if (event.getState() == null) {
            return;
        }

        if (store == null) {
            log.warn("store is not set");
            return;
        }

        if (applicationEventPublisher == null) {
            log.error("applicationEventPublisher is null");
            return;
        }

        try {
            log.debug("receive event on {} for {}", event.getState(), event.getId());
            if (log.isTraceEnabled()) {
                log.trace("event: {}", event);
            }

            //load details
            BaseDTO dto = event.getDto();
            if (dto == null) {
                log.error("Missing dto");
                return;
            }

            //read from dto status when available
            StatusFieldAccessor accessor = (dto instanceof StatusDTO)
                ? StatusFieldAccessor.with(((StatusDTO) dto).getStatus())
                : StatusFieldAccessor.with(Collections.emptyMap());

            String state = event.getState() != null ? event.getState() : accessor.getState();

            if (state == null) {
                log.error("Missing or invalid state");
                return;
            }

            //pick matching jobs
            List<LifecycleTriggerJob> jobs = store.findMatching(j ->
                matcher.match(j.getKey(), dto.getKey()) && j.getStates().contains(state)
            );

            //submit fire for all these jobs
            jobs.forEach(job -> {
                //sanity check: project id must match
                if (!job.getProject().equals(dto.getProject())) {
                    return;
                }

                //build a run for this job
                Map<String, Serializable> details = Map.of(
                    "timestamp",
                    Date.from(Instant.now()).getTime(),
                    "key",
                    job.getKey(),
                    "state",
                    state,
                    "input",
                    dto
                );

                //inflate job with relationships
                job.setRelationships(List.of(new RelationshipDetail(RelationshipName.CONSUMES, null, dto.getKey())));

                TriggerRun<LifecycleTriggerJob> run = TriggerRun
                    .<LifecycleTriggerJob>builder()
                    .job(job)
                    .details(details)
                    .build();

                TriggerExecutionEvent<LifecycleTriggerJob> e = TriggerExecutionEvent
                    .<LifecycleTriggerJob>builder()
                    .run(run)
                    .event(TriggerEvent.FIRE)
                    .build();
                applicationEventPublisher.publishEvent(e);
            });
        } catch (StoreException e) {
            log.error("Error with store", e.getMessage());
        }
    }
}
