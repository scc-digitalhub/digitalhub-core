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

package it.smartcommunitylabdhub.functions.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.core.controllers.BaseEntityController;
import it.smartcommunitylabdhub.functions.filters.FunctionEntityFilter;
import it.smartcommunitylabdhub.functions.Function;
import it.smartcommunitylabdhub.functions.FunctionManager;
import it.smartcommunitylabdhub.tasks.Task;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/-/{project}/functions")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "Function context API", description = "Endpoints related to functions management in project")
public class FunctionContextController extends BaseEntityController<Function, FunctionEntityFilter> {

    @Autowired
    private FunctionManager functionManager;

    /*
     * Tasks
     */

    @Operation(summary = "List tasks for a given function")
    @GetMapping(path = "/{id}/tasks", produces = "application/json; charset=UTF-8")
    public List<Task> getTasks(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException, StoreException {
        Function function = entityService.get(id);

        //check for project and name match
        if (!function.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return functionManager.getTasksByFunctionId(id);
    }
}
