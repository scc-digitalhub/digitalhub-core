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
import it.smartcommunitylabdhub.commons.models.workflow.Workflow;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.validation.BindException;

/*
 * Service for managing workflows
 */
public interface WorkflowManager {
    /**
     * List all workflows
     * @param pageable
     * @return
     */
    Page<Workflow> listWorkflows(Pageable pageable) throws SystemException;

    /**
     * List the latest version of every workflow
     * @return
     */
    List<Workflow> listLatestWorkflows() throws SystemException;

    /**
     * List the latest version of every workflow
     * @param pageable
     * @return
     */
    Page<Workflow> listLatestWorkflows(Pageable pageable) throws SystemException;

    /**
     * List all versions of every workflow for a user
     * @param user
     * @return
     */
    List<Workflow> listWorkflowsByUser(@NotNull String user) throws SystemException;

    /**
     * List all versions of every workflow for a project
     * @param project
     * @return
     */
    List<Workflow> listWorkflowsByProject(@NotNull String project) throws SystemException;

    /**
     * List all versions of every workflow for a project
     * @param project
     * @param pageable
     * @return
     */
    Page<Workflow> listWorkflowsByProject(@NotNull String project, Pageable pageable) throws SystemException;

    /**
     * List the latest version of every workflow for a project
     * @param project
     * @return
     */
    List<Workflow> listLatestWorkflowsByProject(@NotNull String project) throws SystemException;

    /**
     * List the latest version of every workflow for a project
     * @param project
     * @param pageable
     * @return
     */
    Page<Workflow> listLatestWorkflowsByProject(@NotNull String project, Pageable pageable) throws SystemException;

    /**
     * List all workflows, with optional filters
     * @param pageable
     * @param filter
     * @return
     */
    Page<Workflow> searchWorkflows(Pageable pageable, @Nullable SearchFilter<Workflow> filter) throws SystemException;

    /**
     * List the latest version of every workflow, with optional filters
     * @param pageable
     * @param filter
     * @return
     */
    Page<Workflow> searchLatestWorkflows(Pageable pageable, @Nullable SearchFilter<Workflow> filter)
        throws SystemException;

    /**
     * List all versions of every workflow, with optional filters
     * @param project
     * @param pageable
     * @param filter
     * @return
     */
    Page<Workflow> searchWorkflowsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<Workflow> filter
    ) throws SystemException;
    /**
     * List the latest version of every workflow, with optional filters
     * @param project
     * @param pageable
     * @param filter
     * @return
     */
    Page<Workflow> searchLatestWorkflowsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<Workflow> filter
    ) throws SystemException;

    /**
     * Find all versions of a given workflow
     * @param project
     * @param name
     * @return
     */
    List<Workflow> findWorkflows(@NotNull String project, @NotNull String name) throws SystemException;

    /**
     * Find all versions of a given workflow
     * @param project
     * @param name
     * @param pageable
     * @return
     */
    Page<Workflow> findWorkflows(@NotNull String project, @NotNull String name, Pageable pageable)
        throws SystemException;

    /**
     * Find a specific workflow (version) via unique ID. Returns null if not found
     * @param id
     * @return
     */
    @Nullable
    Workflow findWorkflow(@NotNull String id) throws SystemException;

    /**
     * Get a specific workflow (version) via unique ID. Throws exception if not found
     * @param id
     * @return
     * @throws NoSuchEntityException
     */
    Workflow getWorkflow(@NotNull String id) throws NoSuchEntityException, SystemException;

    /**
     * Get the latest version of a given workflow
     * @param project
     * @param name
     * @return
     * @throws NoSuchEntityException
     */
    Workflow getLatestWorkflow(@NotNull String project, @NotNull String name)
        throws NoSuchEntityException, SystemException;

    /**
     * Create a new workflow and store it
     * @param workflowDTO
     * @return
     * @throws DuplicatedEntityException
     */
    Workflow createWorkflow(@NotNull Workflow workflowDTO)
        throws DuplicatedEntityException, BindException, IllegalArgumentException, SystemException;

    /**
     * Update a specific workflow version
     * @param id
     * @param workflowDTO
     * @return
     * @throws NoSuchEntityException
     */
    Workflow updateWorkflow(@NotNull String id, @NotNull Workflow workflowDTO)
        throws NoSuchEntityException, BindException, IllegalArgumentException, SystemException;

    /**
     * Update a specific workflow version
     * @param id
     * @param workflowDTO
     * @param force
     * @return
     * @throws NoSuchEntityException
     */
    Workflow updateWorkflow(@NotNull String id, @NotNull Workflow workflowDTO, boolean force)
        throws NoSuchEntityException, SystemException;
    /**
     * Delete a specific workflow (version) via unique ID
     * @param id
     * @param cascade
     */
    void deleteWorkflow(@NotNull String id, @Nullable Boolean cascade) throws SystemException;

    /**
     * Delete all versions of a given workflow
     * @param project
     * @param name
     */
    void deleteWorkflows(@NotNull String project, @NotNull String name) throws SystemException;

    /**
     * Delete all workflows for a given project, with cascade.
     * @param project
     */
    void deleteWorkflowsByProject(@NotNull String project) throws SystemException;

    /**
     * List all tasks for a given workflow
     * @param workflow
     * @return
     */
    List<Task> getTasksByWorkflowId(@NotNull String workflow) throws SystemException;
}
