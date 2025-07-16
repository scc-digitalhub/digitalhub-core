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

import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.validation.BindException;

/*
 * Entity service for DTOs with SPEC + STATUS, optional lifecycle management included.
 * If tools available, specs are parsed and validated.
 * Supports search via filters.
 */
public interface EntityService<D extends BaseDTO> {
    EntityName getType();

    D create(@NotNull D dto) throws BindException, IllegalArgumentException, DuplicatedEntityException, StoreException;

    D update(@NotNull String id, @NotNull D dto)
        throws BindException, IllegalArgumentException, NoSuchEntityException, StoreException;
    D update(@NotNull String id, @NotNull D dto, boolean forceUpdate)
        throws BindException, IllegalArgumentException, NoSuchEntityException, StoreException;

    void delete(@NotNull String id, @Nullable Boolean cascade) throws StoreException;
    void deleteAll(@Nullable Boolean cascade) throws StoreException;
    void deleteByUser(@NotNull String user, @Nullable Boolean cascade) throws StoreException;
    void deleteByProject(@NotNull String project, @Nullable Boolean cascade) throws StoreException;
    void deleteByKind(@NotNull String kind, @Nullable Boolean cascade) throws StoreException;

    D find(@NotNull String id) throws StoreException;
    D get(@NotNull String id) throws NoSuchEntityException, StoreException;

    List<D> listAll() throws StoreException;
    Page<D> list(Pageable page) throws StoreException;

    List<D> listByUser(@NotNull String user) throws StoreException;
    Page<D> listByUser(@NotNull String user, Pageable page) throws StoreException;

    List<D> listByProject(@NotNull String project) throws StoreException;
    Page<D> listByProject(@NotNull String project, Pageable page) throws StoreException;

    List<D> listByKind(@NotNull String kind) throws StoreException;
    Page<D> listByKind(@NotNull String kind, Pageable page) throws StoreException;

    List<D> search(SearchFilter<D> filter) throws StoreException;
    Page<D> search(SearchFilter<D> filter, Pageable page) throws StoreException;

    List<D> searchByProject(@NotNull String project, SearchFilter<D> filter) throws StoreException;
    Page<D> searchByProject(@NotNull String project, SearchFilter<D> filter, Pageable page) throws StoreException;
}
