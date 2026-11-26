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

package it.smartcommunitylabdhub.triggers.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.core.controllers.BaseEntityController;
import it.smartcommunitylabdhub.lifecycle.LifecycleManager;
import it.smartcommunitylabdhub.triggers.Trigger;
import it.smartcommunitylabdhub.triggers.filters.TriggerEntityFilter;
import it.smartcommunitylabdhub.triggers.lifecycle.TriggerEvent;
import it.smartcommunitylabdhub.triggers.lifecycle.TriggerState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/-/{project}/triggers")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "Trigger context API", description = "Endpoints related to triggers management in Context")
public class TriggerContextController extends BaseEntityController<Trigger, TriggerEntityFilter> {

    @Autowired
    private LifecycleManager<Trigger> lifecycleManager;

    @Override
    @Operation(summary = "Create a trigger in a project context")
    public Trigger create(String project, Trigger dto)
        throws DuplicatedEntityException, IllegalArgumentException, BindException, StoreException {
        //enforce project match
        dto.setProject(project);

        //create as new, will check for duplicated
        Trigger trigger = super.create(project, dto);

        if (
            trigger != null &&
            TriggerState.CREATED.name().equals(StatusFieldAccessor.with(trigger.getStatus()).getState())
        ) {
            //run
            lifecycleManager.perform(trigger, TriggerEvent.RUN.name());
        }

        return trigger;
    }

    @Override
    @Operation(summary = "Delete a specific trigger, with optional cascade")
    public void deleteById(String project, String id, Boolean cascade) throws NoSuchEntityException, StoreException {
        Trigger trigger = entityService.get(id);

        //check for project  match
        if (!trigger.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        //TODO via manager to avoid race condition between cleanup and delete
        try {
            lifecycleManager.perform(trigger, TriggerEvent.STOP.name());
        } catch (Exception e) {
            log.debug("Error stopping trigger: {}", e.getMessage());
        }
        entityService.delete(id, cascade != null && cascade);
    }
}
