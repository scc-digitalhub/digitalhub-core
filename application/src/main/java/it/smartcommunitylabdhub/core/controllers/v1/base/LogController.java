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

package it.smartcommunitylabdhub.core.controllers.v1.base;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.log.Log;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.services.LogService;
import it.smartcommunitylabdhub.core.ApplicationKeys;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.logs.filter.LogEntityFilter;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@ApiVersion("v1")
@RequestMapping("/logs")
//TODO evaluate permissions for project via lookup in dto
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Validated
@Slf4j
@Tag(name = "Log base API", description = "Endpoints related to logs management")
public class LogController {

    @Autowired
    LogService logService;

    @Operation(summary = "Create log", description = "Create a log and return")
    @PostMapping(
        value = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Log createLog(@RequestBody @Valid @NotNull Log dto)
        throws DuplicatedEntityException, IllegalArgumentException, SystemException, BindException {
        return logService.createLog(dto);
    }

    @Operation(summary = "List logs", description = "Return a list of all logs")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<Log> getLogs(
        @ParameterObject @Valid @Nullable LogEntityFilter filter,
        @ParameterObject @PageableDefault(page = 0, size = ApplicationKeys.DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "kind", direction = Direction.ASC) }
        ) Pageable pageable
    ) {
        SearchFilter<Log> sf = null;
        if (filter != null) {
            sf = filter.toSearchFilter();
        }

        return logService.searchLogs(pageable, sf);
    }

    @Operation(summary = "Get a log by id", description = "Return a log")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public Log getLog(@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id)
        throws NoSuchEntityException {
        return logService.getLog(id);
    }

    @Operation(summary = "Update specific log", description = "Update and return the log")
    @PutMapping(
        path = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Log updateLog(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull Log dto
    ) throws NoSuchEntityException, IllegalArgumentException, SystemException, BindException {
        return logService.updateLog(id, dto);
    }

    @Operation(summary = "Delete a log", description = "Delete a specific log")
    @DeleteMapping(path = "/{id}")
    public void deleteLog(@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id) {
        logService.deleteLog(id);
    }
}
