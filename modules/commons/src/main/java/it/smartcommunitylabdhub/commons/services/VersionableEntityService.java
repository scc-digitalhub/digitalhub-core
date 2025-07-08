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

package it.smartcommunitylabdhub.commons.services;

import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;

/*
 * Entity service for VERSIONABLE DTOs, where versions are grouped by `project`+`name` and identified by `id`.
 */
public interface VersionableEntityService<D extends BaseDTO> {
    void deleteAll(@NotNull String project, @NotNull String name, @Nullable Boolean cascade) throws StoreException;

    List<D> findAll(@NotNull String project, @NotNull String name) throws StoreException;
    Page<D> findAll(@NotNull String project, @NotNull String name, Pageable page) throws StoreException;

    D getLatest(@NotNull String project, @NotNull String name) throws NoSuchEntityException, StoreException;

    List<D> listLatest() throws StoreException;
    Page<D> listLatest(Pageable page) throws StoreException;

    List<D> searchLatest(SearchFilter<D> filter) throws StoreException;
    Page<D> searchLatest(SearchFilter<D> filter, Pageable page) throws StoreException;

    List<D> listLatestByProject(@NotNull String project) throws StoreException;
    Page<D> listLatestByProject(@NotNull String project, Pageable page) throws StoreException;

    List<D> searchLatestByProject(@NotNull String project, SearchFilter<D> filter) throws StoreException;
    Page<D> searchLatestByProject(@NotNull String project, SearchFilter<D> filter, Pageable page) throws StoreException;
}
