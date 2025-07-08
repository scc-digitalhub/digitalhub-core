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
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.run.Run;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.validation.BindException;

/*
 * Service for managing runs
 */
public interface RunManager {
    /*
     * Tasks
     */
    List<Run> getRunsByTaskId(@NotNull String taskId) throws SystemException;

    void deleteRunsByTaskId(@NotNull String taskId) throws SystemException;

    /**
     * List all runs
     *
     * @param pageable
     * @return
     */
    Page<Run> listRuns(Pageable pageable) throws SystemException;

    /**
     * List all runs for a given user
     *
     * @param user
     * @return
     */
    List<Run> listRunsByUser(@NotNull String user) throws SystemException;

    /**
     * List all runs for a given project
     *
     * @param project
     * @return
     */
    List<Run> listRunsByProject(@NotNull String project) throws SystemException;

    /**
     * List all runs for a given project
     *
     * @param project
     * @param pageable
     * @return
     */
    Page<Run> listRunsByProject(@NotNull String project, Pageable pageable) throws SystemException;

    /**
     * List all runs, with optional filters
     * @param pageable
     * @param filter
     * @return
     */
    Page<Run> searchRuns(Pageable pageable, @Nullable SearchFilter<Run> filter) throws SystemException;

    /**
     * List the runs for a given project, with optional filters
     * @param project
     * @param pageable
     * @param filter
     * @return
     */
    Page<Run> searchRunsByProject(@NotNull String project, Pageable pageable, @Nullable SearchFilter<Run> filter)
        throws SystemException;

    /**
     * Find a specific run via unique ID. Returns null if not found
     *
     * @param id
     * @return
     */
    @Nullable
    Run findRun(@NotNull String id) throws SystemException;

    /**
     * Get a specific run via unique ID. Throws exception if not found
     *
     * @param id
     * @return
     * @throws NoSuchEntityException
     */
    Run getRun(@NotNull String id) throws NoSuchEntityException, SystemException;

    /**
     * Create a new run and store it
     *
     * @param runDTO
     * @return
     * @throws DuplicatedEntityException
     */
    Run createRun(@NotNull Run runDTO)
        throws DuplicatedEntityException, NoSuchEntityException, BindException, SystemException;

    /**
     * Update a specific run
     *
     * @param id
     * @param runDTO
     * @return
     * @throws NoSuchEntityException
     */
    Run updateRun(@NotNull String id, @NotNull Run runDTO)
        throws NoSuchEntityException, BindException, IllegalArgumentException, SystemException;

    /**
     * Delete a specific run via unique ID, with optional cascade
     *
     * @param id
     * @param cascade
     */
    void deleteRun(@NotNull String id, @Nullable Boolean cascade) throws SystemException;
}
