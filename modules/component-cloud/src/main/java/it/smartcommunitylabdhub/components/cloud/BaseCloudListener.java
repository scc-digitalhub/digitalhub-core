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

package it.smartcommunitylabdhub.components.cloud;

import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.components.websocket.UserNotification;
import it.smartcommunitylabdhub.components.websocket.UserNotificationEntityEvent;
import it.smartcommunitylabdhub.components.websocket.UserNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public abstract class BaseCloudListener<D extends BaseDTO & StatusDTO> {

    protected UserNotificationService notificationService;

    @Autowired(required = false)
    public void setNotificationService(UserNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    protected void notify(UserNotificationEntityEvent<D> event) {
        D dto = event.getDto();
        String user = event.getUser();

        if (event.getUser() == null) {
            log.warn("notification has no user");
            return;
        }

        //resolve entity name
        EntityName entityName = EntityUtils.getEntityName(dto.getClass());

        if (notificationService != null) {
            log.debug("receive notify for {}: {}", user, dto.getId());

            //unpack and notify
            UserNotification<D> notification = UserNotification
                .<D>builder()
                .action(event.getAction())
                .entity(entityName)
                .user(user)
                .dto(dto)
                .build();

            notificationService.notifyUser(notification);
        }
    }

    protected void broadcast(CloudEntityEvent<D> event) {
        D dto = event.getDto();

        if (dto != null) {
            log.debug("receive event for {}: {}", dto.getId(), event.getAction());

            if (notificationService != null && event.getDto() != null) {
                //resolve entity name
                EntityName entityName = EntityUtils.getEntityName(dto.getClass());

                //unpack and notify
                UserNotification<D> notification = UserNotification
                    .<D>builder()
                    .action(event.getAction())
                    .entity(entityName)
                    .dto(event.getDto())
                    .build();

                //forward all events to users via notification
                notificationService.broadcast(notification);
            }
        }
    }
}
