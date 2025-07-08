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

package it.smartcommunitylabdhub.core.websocket;

import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserNotificationService {

    public static final String PREFIX = "/notifications";
    public static final String USER_PREFIX = "/user/";

    private final SimpMessagingTemplate messagingTemplate;

    public UserNotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcast(@NotNull UserNotification<? extends BaseDTO> notification) {
        if (notification.getUser() != null) {
            log.warn("notification has a user");
            return;
        }

        log.debug("notify {} {} change", notification.getEntity(), notification.getId());
        if (log.isTraceEnabled()) {
            log.trace("dto: {}", notification.getDto());
        }

        //send whole event as payload
        broadcast(buildDestination(notification.getEntity(), notification.getDto().getId()), notification);
        broadcast(buildDestination(notification.getEntity()), notification);
    }

    public void broadcast(String destination, Serializable payload) {
        log.debug("broadcast to {}", destination);

        if (log.isTraceEnabled()) {
            log.trace("payload: {}", payload);
        }

        try {
            //send to user via webSocket
            messagingTemplate.convertAndSend(destination, payload);
        } catch (MessagingException e) {
            log.error("Error sending message", e);
        }
    }

    public void notifyUser(@NotNull UserNotification<? extends BaseDTO> notification) {
        if (notification.getUser() == null) {
            log.warn("notification has no user");
            return;
        }

        log.debug(
            "notify {} {} change to user {}",
            notification.getEntity(),
            notification.getId(),
            notification.getUser()
        );
        if (log.isTraceEnabled()) {
            log.trace("dto: {}", notification.getDto());
        }

        //send whole event as payload
        notify(notification.getUser(), buildDestination(notification.getEntity()), notification);
    }

    public void notifyOwner(@NotNull BaseDTO dto) {
        if (dto.getUser() == null) {
            log.warn("dto has no user");
            if (log.isTraceEnabled()) {
                log.trace("dto: {}", dto);
            }

            return;
        }

        log.debug("notify dto {} change", dto.getId());
        if (log.isTraceEnabled()) {
            log.trace("dto: {}", dto);
        }

        //send dto as payload
        notify(dto.getUser(), buildDestination(dto.getClass()), dto);
    }

    public void notify(String user, String destination, Serializable payload) {
        log.debug("notify {} to {}", user, destination);

        if (log.isTraceEnabled()) {
            log.trace("payload: {}", payload);
        }

        try {
            //send to user via webSocket
            messagingTemplate.convertAndSendToUser(user, destination, payload);
        } catch (MessagingException e) {
            log.error("Error sending message", e);
        }
    }

    private String buildDestination(EntityName name) {
        //use simple name  (pluralized) as dest
        return PREFIX + "/" + name.getValue().toLowerCase() + "s";
    }

    private String buildDestination(EntityName name, String id) {
        //use simple name  (pluralized) as dest
        return PREFIX + "/" + name.getValue().toLowerCase() + "s" + "/" + id;
    }

    private String buildDestination(Class<?> clazz) {
        //use simple name  (pluralized) as dest
        return PREFIX + "/" + clazz.getSimpleName().toLowerCase() + "s";
    }
}
