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
import it.smartcommunitylabdhub.commons.models.model.Model;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.services.ModelManager;
import it.smartcommunitylabdhub.core.ApplicationKeys;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.models.filters.ModelEntityFilter;
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
@RequestMapping("/models")
//TODO evaluate permissions for project via lookup in dto
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Validated
@Slf4j
@Tag(name = "Model base API", description = "Endpoints related to models management out of the Context")
public class ModelController {

    @Autowired
    ModelManager modelManager;

    @Autowired
    IndexableEntityService<Model> indexService;

    @Operation(summary = "Create model", description = "Create a model and return")
    @PostMapping(
        value = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Model createModel(@RequestBody @Valid @NotNull Model dto)
        throws DuplicatedEntityException, SystemException, IllegalArgumentException, BindException {
        return modelManager.createModel(dto);
    }

    @Operation(summary = "List models", description = "Return a list of all models")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<Model> getModels(
        @ParameterObject @Valid @Nullable ModelEntityFilter filter,
        @ParameterObject @RequestParam(required = false, defaultValue = "all") String versions,
        @ParameterObject @PageableDefault(page = 0, size = ApplicationKeys.DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "created", direction = Direction.DESC) }
        ) Pageable pageable
    ) {
        SearchFilter<Model> sf = null;
        if (filter != null) {
            sf = filter.toSearchFilter();
        }
        if ("latest".equals(versions)) {
            return modelManager.searchLatestModels(pageable, sf);
        } else {
            return modelManager.searchModels(pageable, sf);
        }
    }

    @Operation(summary = "Get a model by id", description = "Return a model")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public Model getModel(@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id)
        throws NoSuchEntityException {
        return modelManager.getModel(id);
    }

    @Operation(summary = "Update specific model", description = "Update and return the model")
    @PutMapping(
        path = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Model updateModel(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull Model dto
    ) throws NoSuchEntityException, SystemException, IllegalArgumentException, BindException {
        return modelManager.updateModel(id, dto);
    }

    @Operation(summary = "Delete a model", description = "Delete a specific model")
    @DeleteMapping(path = "/{id}")
    public void deleteModel(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam(required = false) Boolean cascade
    ) {
        modelManager.deleteModel(id, cascade);
    }

    /*
     * Search apis
     */
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Reindex all models", description = "Reindex models")
    @PostMapping(value = "/search/reindex", produces = "application/json; charset=UTF-8")
    public void reindexModels() {
        //via async
        indexService.reindexAll();
    }
}
