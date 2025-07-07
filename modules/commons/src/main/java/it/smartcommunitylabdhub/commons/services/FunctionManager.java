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
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.task.Task;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.validation.BindException;

/*
 * Service for managing function
 */
public interface FunctionManager {
    /**
     * List all functions
     * @param pageable
     * @return
     */
    Page<Function> listFunctions(Pageable pageable) throws SystemException;

    /**
     * List the latest version of every function
     * @return
     */
    List<Function> listLatestFunctions() throws SystemException;

    /**
     * List the latest version of every function
     * @param pageable
     * @return
     */
    Page<Function> listLatestFunctions(Pageable pageable) throws SystemException;

    /**
     * List all versions of every function for a user
     * @param user
     * @return
     */
    List<Function> listFunctionsByUser(@NotNull String user) throws SystemException;

    /**
     * List all versions of every function for a project
     * @param project
     * @return
     */
    List<Function> listFunctionsByProject(@NotNull String project) throws SystemException;

    /**
     * List all versions of every function for a project
     * @param project
     * @param pageable
     * @return
     */
    Page<Function> listFunctionsByProject(@NotNull String project, Pageable pageable) throws SystemException;

    /**
     * List the latest version of every function for a project
     * @param project
     * @return
     */
    List<Function> listLatestFunctionsByProject(@NotNull String project) throws SystemException;

    /**
     * List the latest version of every function for a project
     * @param project
     * @param pageable
     * @return
     */
    Page<Function> listLatestFunctionsByProject(@NotNull String project, Pageable pageable) throws SystemException;

    /**
     * List all functions, with optional filters
     * @param pageable
     * @param filter
     * @return
     */
    Page<Function> searchFunctions(Pageable pageable, @Nullable SearchFilter<Function> filter) throws SystemException;

    /**
     * List the latest version of every function, with optional filters
     * @param pageable
     * @param filter
     * @return
     */
    Page<Function> searchLatestFunctions(Pageable pageable, @Nullable SearchFilter<Function> filter)
        throws SystemException;
    /**
     * List all version of every function, with optional filters
     * @param project
     * @param pageable
     * @param filter
     * @return
     */
    Page<Function> searchFunctionsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<Function> filter
    ) throws SystemException;

    /**
     * List the latest version of every function, with optional filters
     * @param project
     * @param pageable
     * @param filter
     * @return
     */
    Page<Function> searchLatestFunctionsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<Function> filter
    ) throws SystemException;

    /**
     * Find all versions of a given function
     * @param project
     * @param name
     * @return
     */
    List<Function> findFunctions(@NotNull String project, @NotNull String name) throws SystemException;

    /**
     * Find all versions of a given function
     * @param project
     * @param name
     * @param pageable
     * @return
     */
    Page<Function> findFunctions(@NotNull String project, @NotNull String name, Pageable pageable)
        throws SystemException;

    /**
     * Find a specific function (version) via unique ID. Returns null if not found
     * @param id
     * @return
     */
    @Nullable
    Function findFunction(@NotNull String id) throws SystemException;

    /**
     * Get a specific function (version) via unique ID. Throws exception if not found
     * @param id
     * @return
     * @throws NoSuchEntityException
     */
    Function getFunction(@NotNull String id) throws NoSuchEntityException, SystemException;

    /**
     * Get the latest version of a given function
     * @param project
     * @param name
     * @return
     * @throws NoSuchEntityException
     */
    Function getLatestFunction(@NotNull String project, @NotNull String name)
        throws NoSuchEntityException, SystemException;

    /**
     * Create a new function and store it
     * @param functionDTO
     * @return
     * @throws DuplicatedEntityException
     */
    Function createFunction(@NotNull Function functionDTO)
        throws DuplicatedEntityException, BindException, SystemException;

    /**
     * Update a specific function version
     * @param id
     * @param functionDTO
     * @return
     * @throws NoSuchEntityException
     */
    Function updateFunction(@NotNull String id, @NotNull Function functionDTO)
        throws NoSuchEntityException, BindException, IllegalArgumentException, SystemException;

    /**
     * Update a specific function version
     * @param id
     * @param functionDTO
     * @param force
     * @return
     * @throws NoSuchEntityException
     */
    Function updateFunction(@NotNull String id, @NotNull Function functionDTO, boolean force)
        throws NoSuchEntityException, SystemException;

    /**
     * Delete a specific function (version) via unique ID, with optional cascade
     * @param id
     */
    void deleteFunction(@NotNull String id, @Nullable Boolean cascade) throws SystemException;

    /**
     * Delete all versions of a given function, with cascade
     * @param project
     * @param name
     */
    void deleteFunctions(@NotNull String project, @NotNull String name) throws SystemException;

    /**
     * Delete all functions for a given project, with cascade.
     * @param project
     */
    void deleteFunctionsByProject(@NotNull String project) throws SystemException;

    /**
     * List all tasks for a given function
     * @param function
     * @return
     */
    List<Task> getTasksByFunctionId(@NotNull String functionId) throws SystemException;
}
