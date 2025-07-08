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
import it.smartcommunitylabdhub.commons.models.task.Task;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.validation.BindException;

/*
 * Service for managing tasks
 */
public interface TaskService {
    /**
     * List all tasks
     * @param pageable
     * @return
     */
    Page<Task> listTasks(Pageable pageable) throws SystemException;

    /**
     * List all tasks for a given user
     * @param user
     * @return
     */
    List<Task> listTasksByUser(@NotNull String user) throws SystemException;

    /**
     * List all tasks for a given project
     * @param project
     * @return
     */
    List<Task> listTasksByProject(@NotNull String project) throws SystemException;

    /**
     * List all tasks for a given project
     * @param project
     * @param pageable
     * @return
     */
    Page<Task> listTasksByProject(@NotNull String project, Pageable pageable) throws SystemException;

    /**
     * List all tasks, with optional filters
     * @param pageable
     * @param filter
     * @return
     */
    Page<Task> searchTasks(Pageable pageable, @Nullable SearchFilter<Task> filter) throws SystemException;

    /**
     * List the latest version of every task, with optional filters
     * @param project
     * @param pageable
     * @param filter
     * @return
     */
    Page<Task> searchTasksByProject(@NotNull String project, Pageable pageable, @Nullable SearchFilter<Task> filter)
        throws SystemException;

    /**
     * Find a specific task  via unique ID. Returns null if not found
     * @param id
     * @return
     */
    @Nullable
    Task findTask(@NotNull String id) throws SystemException;

    /**
     * Get a specific task via unique ID. Throws exception if not found
     * @param id
     * @return
     * @throws NoSuchEntityException
     */
    Task getTask(@NotNull String id) throws NoSuchEntityException, SystemException;

    /**
     * Create a new task and store it
     * @param taskDTO
     * @return
     * @throws DuplicatedEntityException
     */
    Task createTask(@NotNull Task taskDTO)
        throws DuplicatedEntityException, BindException, IllegalArgumentException, SystemException;

    /**
     * Update a specific task
     * @param id
     * @param taskDTO
     * @return
     * @throws NoSuchEntityException
     */
    Task updateTask(@NotNull String id, @NotNull Task taskDTO)
        throws NoSuchEntityException, BindException, IllegalArgumentException, SystemException;

    /**
     * Delete a specific task via unique ID, with optional cascade
     * @param id
     * @param cascade
     */
    void deleteTask(@NotNull String id, @Nullable Boolean cascade) throws SystemException;
}
