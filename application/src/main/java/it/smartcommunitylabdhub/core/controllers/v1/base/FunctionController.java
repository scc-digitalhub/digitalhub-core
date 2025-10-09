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
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.services.FunctionManager;
import it.smartcommunitylabdhub.core.ApplicationKeys;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.functions.filters.FunctionEntityFilter;
import it.smartcommunitylabdhub.search.service.IndexableEntityService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ApiVersion("v1")
@RequestMapping("/functions")
//TODO evaluate permissions for project via lookup in dto
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Validated
@Slf4j
@Tag(name = "Function base API", description = "Endpoints related to functions management out of the Context")
public class FunctionController {

    @Autowired
    FunctionManager functionManager;

    @Autowired
    IndexableEntityService<Function> indexService;

    @Operation(summary = "Create function", description = "Create a function and return")
    @PostMapping(
        value = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Function createFunction(@RequestBody @Valid @NotNull Function dto)
        throws DuplicatedEntityException, SystemException, BindException {
        return functionManager.createFunction(dto);
    }

    @Operation(summary = "List functions", description = "Return a list of all functions")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<Function> getFunctions(
        @ParameterObject @Valid @Nullable FunctionEntityFilter filter,
        @ParameterObject @RequestParam(required = false, defaultValue = "all") String versions,
        @ParameterObject @PageableDefault(page = 0, size = ApplicationKeys.DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "id", direction = Direction.ASC) }
        ) Pageable pageable
    ) {
        SearchFilter<Function> sf = null;
        if (filter != null) {
            sf = filter.toSearchFilter();
        }
        if ("latest".equals(versions)) {
            return functionManager.searchLatestFunctions(pageable, sf);
        } else {
            return functionManager.searchFunctions(pageable, sf);
        }
    }

    @Operation(summary = "Get a function by id", description = "Return a function")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public Function getFunction(@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id)
        throws NoSuchEntityException {
        return functionManager.getFunction(id);
    }

    @Operation(summary = "Update specific function", description = "Update and return the function")
    @PutMapping(
        path = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Function updateFunction(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull Function dto
    ) throws NoSuchEntityException, IllegalArgumentException, SystemException, BindException {
        return functionManager.updateFunction(id, dto);
    }

    @Operation(summary = "Delete a function", description = "Delete a specific function, with optional cascade on runs")
    @DeleteMapping(path = "/{id}")
    public void deleteFunction(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam(required = false) Boolean cascade
    ) {
        functionManager.deleteFunction(id, cascade);
    }

    /*
     * Search apis
     */
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Reindex all functions", description = "Reindex functions")
    @PostMapping(value = "/search/reindex", produces = "application/json; charset=UTF-8")
    public void reindexFunctions() {
        //via async
        indexService.reindexAll();
    }
}
