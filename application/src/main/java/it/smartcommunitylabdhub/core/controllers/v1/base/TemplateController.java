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
import it.smartcommunitylabdhub.commons.models.template.Template;
import it.smartcommunitylabdhub.core.ApplicationKeys;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.templates.SearchableTemplateService;
import it.smartcommunitylabdhub.core.templates.TemplateFilter;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ApiVersion("v1")
@RequestMapping("/templates")
//TODO evaluate permissions for project via lookup in dto
@PreAuthorize("hasAuthority('ROLE_USER')")
@Validated
@Tag(name = "Template base API", description = "Endpoints related to entity templates management")
public class TemplateController {

    @Autowired
    SearchableTemplateService templateService;

    @Operation(summary = "List templates", description = "Return a list of all templates")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<Template> listTemplates(
        @ParameterObject @Valid @Nullable TemplateFilter filter,
        @ParameterObject @PageableDefault(page = 0, size = ApplicationKeys.DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "id", direction = Direction.ASC) }
        ) Pageable pageable
    ) {
        if (filter == null) filter = new TemplateFilter();
        return templateService.searchTemplates(pageable, filter);
    }

    @Operation(summary = "List entity's templates", description = "Return a list of all entity's templates")
    @GetMapping(path = "/{entity}", produces = "application/json; charset=UTF-8")
    public Page<Template> getTemplates(
        @PathVariable @NotNull String entity,
        @ParameterObject @Valid @Nullable TemplateFilter filter,
        @ParameterObject @PageableDefault(page = 0, size = ApplicationKeys.DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "id", direction = Direction.ASC) }
        ) Pageable pageable
    ) {
        if (filter == null) filter = new TemplateFilter();
        return templateService.searchTemplates(pageable, entity, filter);
    }

    @Operation(summary = "Get specific template", description = "Return a specific template")
    @GetMapping(path = "/{entity}/{id}", produces = "application/json; charset=UTF-8")
    public Template getOne(@PathVariable @NotNull String entity, @PathVariable @NotNull String id) {
        return templateService.getTemplate(entity, id);
    }
}
