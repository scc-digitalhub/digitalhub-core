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

package it.smartcommunitylabdhub.trigger.scheduler.quartz;

import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.trigger.scheduler.models.ScheduledTriggerJob;
import it.smartcommunitylabdhub.triggers.infrastructure.TriggerRun;
import it.smartcommunitylabdhub.triggers.lifecycle.TriggerEvent;
import it.smartcommunitylabdhub.triggers.lifecycle.TriggerExecutionEvent;
import it.smartcommunitylabdhub.triggers.lifecycle.TriggerJob;
import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Slf4j
public class SchedulerJob extends QuartzJobBean {

    ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    protected void executeInternal(@NonNull JobExecutionContext context) throws JobExecutionException {
        if (applicationEventPublisher == null) {
            log.error("applicationEventPublisher is null");
            return;
        }

        try {
            //fetch job data and publish FIRE event
            byte[] bytes = (byte[]) context.getJobDetail().getJobDataMap().get("job");
            ScheduledTriggerJob job = bytes != null
                ? JacksonMapper.CBOR_OBJECT_MAPPER.readValue(bytes, ScheduledTriggerJob.class)
                : null;

            log.debug("triggered {}", job.getId());
            if (log.isTraceEnabled()) {
                log.trace("job: {}", job);
            }

            //build a run for this job
            Map<String, Serializable> details = Map.of(
                "timestamp",
                Date.from(Instant.now()).getTime(),
                "schedule",
                job.getSchedule()
            );
            TriggerRun<TriggerJob> run = TriggerRun.<TriggerJob>builder().job(job).details(details).build();

            TriggerExecutionEvent<TriggerJob> event = TriggerExecutionEvent
                .builder()
                .run(run)
                .event(TriggerEvent.FIRE)
                .build();
            applicationEventPublisher.publishEvent(event);
        } catch (IOException e) {
            log.error("error deserializing job data: {}", e.getMessage());
        }
    }
}
