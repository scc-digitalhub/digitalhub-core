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

package it.smartcommunitylabdhub.logs.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.logs.Log;
import it.smartcommunitylabdhub.logs.LogService;
import it.smartcommunitylabdhub.logs.filter.LogEntityFilter;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/logs")
//TODO evaluate permissions for project via lookup in dto
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Validated
@Slf4j
@Tag(name = "Log base API", description = "Endpoints related to logs management")
public class LogController {

    public static final int DEFAULT_PAGE_SIZE = 25;

    @Autowired
    LogService logService;

    @Operation(summary = "List logs", description = "Return a list of all logs")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<Log> getLogs(
        @ParameterObject @Valid @Nullable LogEntityFilter filter,
        @ParameterObject @PageableDefault(page = 0, size = DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
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

    @Operation(summary = "Delete a log", description = "Delete a specific log")
    @DeleteMapping(path = "/{id}")
    public void deleteLog(@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id) {
        logService.deleteLog(id);
    }
}
