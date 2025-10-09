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

package it.smartcommunitylabdhub.core.controllers.v1.context;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.trigger.Trigger;
import it.smartcommunitylabdhub.commons.services.TriggerManager;
import it.smartcommunitylabdhub.core.ApplicationKeys;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.triggers.filters.TriggerEntityFilter;
import it.smartcommunitylabdhub.lifecycle.LifecycleManager;
import it.smartcommunitylabdhub.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.relationships.RelationshipsAwareEntityService;
import it.smartcommunitylabdhub.triggers.lifecycle.TriggerEvent;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ApiVersion("v1")
@RequestMapping("/-/{project}/triggers")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "Trigger context API", description = "Endpoints related to triggers management in Context")
public class TriggerContextController {

    @Autowired
    TriggerManager triggerManager;

    @Autowired
    private LifecycleManager<Trigger> lifecycleManager;

    @Autowired
    RelationshipsAwareEntityService<Trigger> relationshipsService;

    @Operation(summary = "Create a trigger in a project context")
    @PostMapping(
        value = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Trigger createTrigger(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @RequestBody @Valid @NotNull Trigger dto
    ) throws DuplicatedEntityException, IllegalArgumentException, SystemException, BindException {
        //enforce project match
        dto.setProject(project);

        //create as new, will check for duplicated
        Trigger trigger = triggerManager.createTrigger(dto);

        //run
        lifecycleManager.perform(trigger, TriggerEvent.RUN.name());

        return trigger;
    }

    @Operation(summary = "Search triggers, with optional filter")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<Trigger> searchTriggers(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @ParameterObject @Valid @Nullable TriggerEntityFilter filter,
        @ParameterObject @PageableDefault(page = 0, size = ApplicationKeys.DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "created", direction = Direction.DESC) }
        ) Pageable pageable
    ) {
        SearchFilter<Trigger> sf = null;
        if (filter != null) {
            sf = filter.toSearchFilter();
        }

        return triggerManager.searchTriggersByProject(project, pageable, sf);
    }

    @Operation(summary = "Retrieve a specific trigger given the trigger id")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public Trigger getTriggerById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Trigger trigger = triggerManager.getTrigger(id);

        //check for project match
        if (!trigger.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return trigger;
    }

    @Operation(summary = "Update if exist a trigger in a project context")
    @PutMapping(
        value = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Trigger updateTriggerById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull Trigger triggerDTO
    ) throws NoSuchEntityException, IllegalArgumentException, SystemException, BindException {
        Trigger trigger = triggerManager.getTrigger(id);

        //check for project match
        if (!trigger.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return triggerManager.updateTrigger(id, triggerDTO);
    }

    @Operation(summary = "Delete a specific trigger, with optional cascade")
    @DeleteMapping(path = "/{id}")
    public void deleteTriggerById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam(required = false) Boolean cascade
    ) throws NoSuchEntityException {
        Trigger trigger = triggerManager.getTrigger(id);

        //check for project  match
        if (!trigger.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        //TODO via manager to avoid race condition between cleanup and delete
        lifecycleManager.perform(trigger, TriggerEvent.STOP.name());
        triggerManager.deleteTrigger(id, cascade != null && cascade);
    }

    @Operation(summary = "Execute a specific trigger")
    @PostMapping(path = "/{id}/run")
    public Trigger runTriggerById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Trigger trigger = triggerManager.getTrigger(id);

        //check for project  match
        if (!trigger.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        // via manager
        return lifecycleManager.perform(trigger, TriggerEvent.RUN.name());
    }

    @Operation(summary = "Stop a specific trigger execution")
    @PostMapping(path = "/{id}/stop")
    public Trigger stopTriggerById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Trigger trigger = triggerManager.getTrigger(id);
        //check for project  match
        if (!trigger.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        // via manager
        return lifecycleManager.perform(trigger, TriggerEvent.STOP.name());
    }

    @Operation(summary = "Get relationships info for a given entity, if available")
    @GetMapping(path = "/{id}/relationships", produces = "application/json; charset=UTF-8")
    public List<RelationshipDetail> getRelationshipsById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Trigger entity = triggerManager.getTrigger(id);

        //check for project and name match
        if ((entity != null) && !entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return relationshipsService.getRelationships(id);
    }
}
