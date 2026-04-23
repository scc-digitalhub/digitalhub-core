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

package it.smartcommunitylabdhub.extensions.controller;

import io.swagger.v3.oas.annotations.Operation;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.core.services.EntityService;
import it.smartcommunitylabdhub.extensions.ExtensionManager;
import it.smartcommunitylabdhub.extensions.model.Extension;
import it.smartcommunitylabdhub.extensions.persistence.ExtensionBuilder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

public abstract class BaseExtensibleEntityController<D extends BaseDTO> {

    protected EntityService<D> entityService;
    protected ExtensionManager extensionManager;

    @Autowired
    public void setEntityService(EntityService<D> entityService) {
        this.entityService = entityService;
    }

    @Autowired
    public void setExtensionManager(ExtensionManager extensionManager) {
        this.extensionManager = extensionManager;
    }

    @Operation(summary = "Get extensions for a given entity, if available")
    @GetMapping(path = "/{id}/extensions", produces = "application/json; charset=UTF-8")
    public List<Extension> getDExtensionsById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException, StoreException {
        D dto = entityService.get(id);

        //check for project and name match
        if (!dto.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return extensionManager.listExtensionsByParent(ExtensionBuilder.from(dto).getParent());
    }

    @Operation(summary = "Store extensions for a given entity, if available")
    @PutMapping(path = "/{id}/extensions", produces = "application/json; charset=UTF-8")
    public void storeExtensionsById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody List<Extension> extensions
    ) throws NoSuchEntityException, IllegalArgumentException, SystemException, BindException, StoreException {
        D dto = entityService.get(id);

        //check for project and name match
        if (!dto.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        if (extensions != null) {
            //enforce parent+project
            String parent = ExtensionBuilder.from(dto).getParent();
            List<Extension> previous = extensionManager.listExtensionsByParent(parent);
            List<Extension> updated = new ArrayList<>();

            //update or create as defined
            for (Extension ext : extensions) {
                ext.setParent(parent);
                ext.setProject(dto.getProject());
                Extension e = extensionManager.createOrUpdateExtension(ext);
                updated.add(e);
            }

            //delete removed
            List<Extension> toDelete = previous
                .stream()
                .filter(e -> updated.stream().noneMatch(u -> u.getId().equals(e.getId())))
                .toList();
            for (Extension ext : toDelete) {
                extensionManager.deleteExtension(ext.getId());
            }
        }
    }
}
