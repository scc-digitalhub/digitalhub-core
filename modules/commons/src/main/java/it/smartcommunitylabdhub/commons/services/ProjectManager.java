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
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.validation.BindException;

/*
 * Service for managing projects
 */
public interface ProjectManager {
    /**
     * List all projects
     * @param pageable
     * @return
     */
    Page<Project> listProjects(Pageable pageable) throws SystemException;

    /**
     * List all projects by user
     * @param user
     * @return
     */
    List<Project> listProjectsByUser(@NotNull String user) throws SystemException;

    /**
     * List all projects, with optional filters
     * @param pageable
     * @param filter
     * @return
     */
    Page<Project> searchProjects(Pageable pageable, @Nullable SearchFilter<Project> filter) throws SystemException;

    /**
     * Find a specific project  via unique ID. Returns null if not found
     * @param id
     * @return
     */
    @Nullable
    Project findProject(@NotNull String id) throws SystemException;

    /**
     * Find a specific project  via unique ID. Returns null if not found
     * @param id
     * @return
     */
    @Nullable
    Project findProjectByName(@NotNull String name) throws SystemException;

    /**
     * Get a specific project via unique ID. Throws exception if not found
     * @param id
     * @return
     * @throws NoSuchEntityException
     */
    Project getProject(@NotNull String id) throws NoSuchEntityException, SystemException;

    /**
     * Get a specific project via unique ID. Throws exception if not found
     * @param id
     * @return
     * @throws NoSuchEntityException
     */
    Project getProjectByName(@NotNull String name) throws NoSuchEntityException, SystemException;

    /**
     * Create a new project and store it
     * @param projectDTO
     * @return
     * @throws DuplicatedEntityException
     */
    Project createProject(@NotNull Project projectDTO)
        throws DuplicatedEntityException, BindException, IllegalArgumentException, SystemException;

    /**
     * Update a specific project
     * @param id
     * @param projectDTO
     * @return
     * @throws NoSuchEntityException
     */
    Project updateProject(@NotNull String id, @NotNull Project projectDTO)
        throws NoSuchEntityException, BindException, IllegalArgumentException, SystemException;

    /**
     * Delete a specific project via unique ID, with optional cascade
     * @param id
     * @param cascade
     */
    void deleteProject(@NotNull String id, @Nullable Boolean cascade) throws SystemException;
}
