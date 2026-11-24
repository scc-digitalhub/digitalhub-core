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

package it.smartcommunitylabdhub.core.controllers;

import io.swagger.v3.oas.annotations.Operation;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.lifecycle.LifecycleManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Slf4j
public abstract class BaseLifecycleEntityController<D extends BaseDTO & StatusDTO> {

    public static final int DEFAULT_PAGE_SIZE = 25;

    protected EntityService<D> entityService;
    protected LifecycleManager<D> lifecycleManager;

    @Autowired
    public void setEntityService(EntityService<D> entityService) {
        this.entityService = entityService;
    }

    @Autowired
    public void setLifecycleManager(LifecycleManager<D> lifecycleManager) {
        this.lifecycleManager = lifecycleManager;
    }

    /*
     * Actions
     */
    @Operation(summary = "Perform action on a specific dto")
    @PostMapping(path = "/{id}/{action}")
    public D perform(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String action
    ) throws NoSuchEntityException, StoreException {
        D dto = entityService.get(id);

        //check for project  match
        if (!dto.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        // via manager
        return lifecycleManager.perform(dto, action.toUpperCase());
    }
}
