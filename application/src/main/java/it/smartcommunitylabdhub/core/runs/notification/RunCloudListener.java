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

package it.smartcommunitylabdhub.core.runs.notification;

import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.core.components.cloud.CloudEntityEvent;
import it.smartcommunitylabdhub.core.websocket.UserNotification;
import it.smartcommunitylabdhub.core.websocket.UserNotificationEntityEvent;
import it.smartcommunitylabdhub.core.websocket.UserNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RunCloudListener {

    private UserNotificationService notificationService;

    @Autowired(required = false)
    public void setNotificationService(UserNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async
    @EventListener
    public void notify(UserNotificationEntityEvent<Run> event) {
        Run dto = event.getDto();
        String user = event.getUser();

        if (event.getUser() == null) {
            log.warn("notification has no user");
            return;
        }

        if (notificationService != null) {
            log.debug("receive notify for {}: {}", user, dto.getId());

            //unpack and notify
            UserNotification<Run> notification = UserNotification
                .<Run>builder()
                .action(event.getAction())
                .entity(EntityName.RUN)
                .user(user)
                .dto(dto)
                .build();

            notificationService.notifyUser(notification);
        }
    }

    @Async
    @EventListener
    public void broadcast(CloudEntityEvent<Run> event) {
        Run run = event.getDto();

        if (run != null) {
            log.debug("receive event for {}: {}", run.getId(), event.getAction());

            if (notificationService != null && event.getDto() != null) {
                //unpack and notify
                UserNotification<Run> notification = UserNotification
                    .<Run>builder()
                    .action(event.getAction())
                    .entity(EntityName.RUN)
                    .dto(event.getDto())
                    .build();

                //forward all events to users via notification
                notificationService.broadcast(notification);
            }
        }
    }
}
