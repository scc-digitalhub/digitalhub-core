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
import it.smartcommunitylabdhub.commons.models.artifact.Artifact;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.validation.BindException;

/*
 * Service for managing artifacts
 */
public interface ArtifactManager {
    /**
     * List all artifacts
     * @param pageable
     * @return
     */
    Page<Artifact> listArtifacts(Pageable pageable) throws SystemException;

    /**
     * List the latest version of every artifact
     * @return
     */
    List<Artifact> listLatestArtifacts() throws SystemException;

    /**
     * List the latest version of every artifact
     * @param pageable
     * @return
     */
    Page<Artifact> listLatestArtifacts(Pageable pageable) throws SystemException;

    /**
     * List all versions of every artifact for a user
     * @param user
     * @return
     */
    List<Artifact> listArtifactsByUser(@NotNull String user) throws SystemException;

    /**
     * List all versions of every artifact for a project
     * @param project
     * @return
     */
    List<Artifact> listArtifactsByProject(@NotNull String project) throws SystemException;

    /**
     * List all versions of every artifact for a project
     * @param project
     * @param pageable
     * @return
     */
    Page<Artifact> listArtifactsByProject(@NotNull String project, Pageable pageable) throws SystemException;

    /**
     * List the latest version of every artifact for a project
     * @param project
     * @return
     */
    List<Artifact> listLatestArtifactsByProject(@NotNull String project) throws SystemException;

    /**
     * List the latest version of every artifact for a project
     * @param project
     * @param pageable
     * @return
     */
    Page<Artifact> listLatestArtifactsByProject(@NotNull String project, Pageable pageable) throws SystemException;

    /**
     * List all artifacts, with optional filters
     * @param pageable
     * @param filter
     * @return
     */
    Page<Artifact> searchArtifacts(Pageable pageable, @Nullable SearchFilter<Artifact> filter) throws SystemException;

    /**
     * List the latest version of every artifact, with optional filters
     * @param pageable
     * @param filter
     * @return
     */
    Page<Artifact> searchLatestArtifacts(Pageable pageable, @Nullable SearchFilter<Artifact> filter)
        throws SystemException;

    /**
     * List all versions of every artifact, with optional filters
     * @param project
     * @param pageable
     * @param filter
     * @return
     */
    Page<Artifact> searchArtifactsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<Artifact> filter
    ) throws SystemException;

    /**
     * List the latest version of every artifact, with optional filters
     * @param project
     * @param pageable
     * @param filter
     * @return
     */
    Page<Artifact> searchLatestArtifactsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<Artifact> filter
    ) throws SystemException;

    /**
     * Find all versions of a given artifact
     * @param project
     * @param name
     * @return
     */
    List<Artifact> findArtifacts(@NotNull String project, @NotNull String name) throws SystemException;

    /**
     * Find all versions of a given artifact
     * @param project
     * @param name
     * @param pageable
     * @return
     */
    Page<Artifact> findArtifacts(@NotNull String project, @NotNull String name, Pageable pageable)
        throws SystemException;

    /**
     * Find a specific artifact (version) via unique ID. Returns null if not found
     * @param id
     * @return
     */
    @Nullable
    Artifact findArtifact(@NotNull String id) throws SystemException;

    /**
     * Get a specific artifact (version) via unique ID. Throws exception if not found
     * @param id
     * @return
     * @throws NoSuchEntityException
     */
    Artifact getArtifact(@NotNull String id) throws NoSuchEntityException, SystemException;

    /**
     * Get the latest version of a given artifact
     * @param project
     * @param name
     * @return
     * @throws NoSuchEntityException
     */
    Artifact getLatestArtifact(@NotNull String project, @NotNull String name)
        throws NoSuchEntityException, SystemException;

    /**
     * Create a new artifact and store it
     * @param artifactDTO
     * @return
     * @throws DuplicatedEntityException
     * @throws IllegalArgumentException
     * @throws BindException
     */
    Artifact createArtifact(@NotNull Artifact artifactDTO)
        throws DuplicatedEntityException, BindException, IllegalArgumentException, SystemException;

    /**
     * Update a specific artifact version
     * @param id
     * @param artifactDTO
     * @return
     * @throws NoSuchEntityException
     */
    Artifact updateArtifact(@NotNull String id, @NotNull Artifact artifactDTO)
        throws NoSuchEntityException, BindException, IllegalArgumentException, SystemException;

    /**
     * Delete a specific artifact (version) via unique ID
     * @param id
     */
    void deleteArtifact(@NotNull String id, @Nullable Boolean cascade) throws SystemException;

    /**
     * Delete all versions of a given artifact
     * @param project
     * @param name
     */
    void deleteArtifacts(@NotNull String project, @NotNull String name, @Nullable Boolean cascade)
        throws SystemException;

    /**
     * Delete all artifacts for a given project, with cascade.
     * @param project
     */
    void deleteArtifactsByProject(@NotNull String project, @Nullable Boolean cascade) throws SystemException;
}
