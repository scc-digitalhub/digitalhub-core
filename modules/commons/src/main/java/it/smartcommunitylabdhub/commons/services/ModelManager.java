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
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.model.Model;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.validation.BindException;

/*
 * Service for managing data items
 */
public interface ModelManager {
    /**
     * List all models
     * @param pageable
     * @return
     */
    Page<Model> listModels(Pageable pageable) throws SystemException;

    /**
     * List the latest version of every model
     * @return
     */
    List<Model> listLatestModels() throws SystemException;

    /**
     * List the latest version of every model
     * @param pageable
     * @return
     */
    Page<Model> listLatestModels(Pageable pageable) throws SystemException;

    /**
     * List all versions of every model for a user
     * @param user
     * @return
     */
    List<Model> listModelsByUser(@NotNull String user) throws SystemException;

    /**
     * List all versions of every model for a project
     * @param project
     * @return
     */
    List<Model> listModelsByProject(@NotNull String project) throws SystemException;

    /**
     * List all versions of every model for a project
     * @param project
     * @param pageable
     * @return
     */
    Page<Model> listModelsByProject(@NotNull String project, Pageable pageable) throws SystemException;

    /**
     * List the latest version of every model for a project
     * @param project
     * @return
     */
    List<Model> listLatestModelsByProject(@NotNull String project) throws SystemException;

    /**
     * List the latest version of every model for a project
     * @param project
     * @param pageable
     * @return
     */
    Page<Model> listLatestModelsByProject(@NotNull String project, Pageable pageable) throws SystemException;

    /**
     * List all models, with optional filters
     * @param pageable
     * @param filter
     * @return
     */
    Page<Model> searchModels(Pageable pageable, @Nullable SearchFilter<Model> filter) throws SystemException;

    /**
     * List the latest version of every model, with optional filters
     * @param pageable
     * @param filter
     * @return
     */
    Page<Model> searchLatestModels(Pageable pageable, @Nullable SearchFilter<Model> filter) throws SystemException;

    /**
     * List all versions of every model, with optional filters
     * @param project
     * @param pageable
     * @param filter
     * @return
     */
    Page<Model> searchModelsByProject(@NotNull String project, Pageable pageable, @Nullable SearchFilter<Model> filter)
        throws SystemException;

    /**
     * List the latest version of every model, with optional filters
     * @param project
     * @param pageable
     * @param filter
     * @return
     */
    Page<Model> searchLatestModelsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<Model> filter
    ) throws SystemException;

    /**
     * Find all versions of a given model
     * @param project
     * @param name
     * @return
     */
    List<Model> findModels(@NotNull String project, @NotNull String name) throws SystemException;

    /**
     * Find all versions of a given model
     * @param project
     * @param name
     * @param pageable
     * @return
     */
    Page<Model> findModels(@NotNull String project, @NotNull String name, Pageable pageable) throws SystemException;

    /**
     * Find a specific model (version) via unique ID. Returns null if not found
     * @param id
     * @return
     */
    @Nullable
    Model findModel(@NotNull String id) throws SystemException;

    /**
     * Get a specific model (version) via unique ID. Throws exception if not found
     * @param id
     * @return
     * @throws NoSuchEntityException
     */
    Model getModel(@NotNull String id) throws NoSuchEntityException, SystemException;

    /**
     * Get the latest version of a given model
     * @param project
     * @param name
     * @return
     * @throws NoSuchEntityException
     */
    Model getLatestModel(@NotNull String project, @NotNull String name) throws NoSuchEntityException, SystemException;

    /**
     * Create a new model and store it
     * @param modelDTO
     * @return
     * @throws DuplicatedEntityException
     * @throws IllegalArgumentException
     * @throws BindException
     */
    Model createModel(@NotNull Model modelDTO)
        throws DuplicatedEntityException, SystemException, BindException, IllegalArgumentException;

    /**
     * Update a specific model version
     * @param id
     * @param modelDTO
     * @return
     * @throws NoSuchEntityException
     * @throws IllegalArgumentException
     * @throws BindException
     */
    Model updateModel(@NotNull String id, @NotNull Model modelDTO)
        throws NoSuchEntityException, SystemException, BindException, IllegalArgumentException;

    /**
     * Delete a specific model (version) via unique ID
     * @param id
     */
    void deleteModel(@NotNull String id, @Nullable Boolean cascade) throws SystemException;

    /**
     * Delete all versions of a given model
     * @param project
     * @param name
     */
    void deleteModels(@NotNull String project, @NotNull String name, @Nullable Boolean cascade) throws SystemException;

    /**
     * Delete all models for a given project, with cascade.
     * @param project
     */
    void deleteModelsByProject(@NotNull String project, @Nullable Boolean cascade) throws SystemException;
}
