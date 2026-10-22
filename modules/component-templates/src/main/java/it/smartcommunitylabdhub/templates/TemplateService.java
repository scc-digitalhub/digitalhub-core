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

package it.smartcommunitylabdhub.templates;

import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.template.Template;
import it.smartcommunitylabdhub.templates.filter.TemplateFilter;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/*
 * Template service for exposing templates for a given entity D
 */
public interface TemplateService<D> {
    Class<D> getType();

    @Nullable
    Template findTemplate(@NotNull String id) throws NoSuchEntityException, SystemException;

    Template getTemplate(@NotNull String id) throws NoSuchEntityException, SystemException;

    List<Template> listTemplates(@Valid TemplateFilter filter) throws SystemException;
    Page<Template> searchTemplates(@Valid TemplateFilter filter, Pageable pageable) throws SystemException;
}
