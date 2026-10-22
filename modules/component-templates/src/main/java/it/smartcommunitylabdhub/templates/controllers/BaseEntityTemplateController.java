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

package it.smartcommunitylabdhub.templates.controllers;

import io.swagger.v3.oas.annotations.Operation;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.template.Template;
import it.smartcommunitylabdhub.templates.TemplateService;
import it.smartcommunitylabdhub.templates.filter.TemplateFilter;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

public abstract class BaseEntityTemplateController<D extends BaseDTO> {

    public static final int DEFAULT_PAGE_SIZE = 25;
    protected TemplateService<D> templateService;

    @Autowired
    public void setTemplateService(TemplateService<D> templateService) {
        this.templateService = templateService;
    }

    @Operation(summary = "List templates", description = "Return a list of all templates")
    @GetMapping(path = "/templates", produces = "application/json; charset=UTF-8")
    public Page<Template> listTemplates(
        @ParameterObject @Valid @Nullable TemplateFilter filter,
        @ParameterObject @PageableDefault(page = 0, size = DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "id", direction = Direction.ASC) }
        ) Pageable pageable
    ) {
        if (filter == null) filter = new TemplateFilter();
        return templateService.searchTemplates(filter, pageable);
    }

    @Operation(summary = "Get specific template", description = "Return a specific template")
    @GetMapping(path = "/templates/{id}", produces = "application/json; charset=UTF-8")
    public Template getTemplate(@PathVariable @NotNull String id) {
        return templateService.getTemplate(id);
    }
}
