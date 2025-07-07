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
import it.smartcommunitylabdhub.commons.models.secret.Secret;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.BindException;

/**
 * Project secret service
 */
public interface SecretService {
    /**
     * List all secrets
     * @param pageable
     * @return
     */
    Page<Secret> listSecrets(Pageable pageable) throws SystemException;

    /**
     * List all the project secrets for user
     * @param user
     * @return
     */
    List<Secret> listSecretsByUser(@NotNull String user) throws SystemException;

    /**
     * List all the project secrets for the project with the specified name
     * @param project
     * @return
     */
    List<Secret> listSecretsByProject(@NotNull String project) throws SystemException;

    /**
     * List all the project secrets for the project with the specified name
     * @param project
     * @param pageable
     * @return
     */

    Page<Secret> listSecretsByProject(@NotNull String project, Pageable pageable) throws SystemException;

    /**
     * Retrieve the secret with the specified id, or null if not found
     * @param id
     * @return
     */
    Secret findSecret(@NotNull String id) throws SystemException;

    /**
     * Retrieve the secret with the specified id. Throw error if not found
     * @param id
     * @return
     */
    Secret getSecret(@NotNull String id) throws NoSuchEntityException, SystemException;

    /**
     * Create new project secret entity and store it in the database. Throw error if the operation cannot be performed.
     * @param secret
     * @return
     */
    Secret createSecret(@NotNull Secret secret)
        throws DuplicatedEntityException, BindException, IllegalArgumentException, SystemException;

    /**
     * Update the secret with the specified id. Throw error if not found or if the operation cannot be performed.
     * @param secret
     * @param id
     * @return
     */
    Secret updateSecret(@NotNull String id, @NotNull Secret secret)
        throws NoSuchEntityException, BindException, IllegalArgumentException, SystemException;

    /**
     * Delete the secret with the specified id. Throw error if not found or if the operation cannot be performed.
     * @param id
     * @return
     */
    void deleteSecret(@NotNull String id) throws SystemException;

    /**
     * Delete all secrets for a given project, with cascade.
     * @param project
     */
    void deleteSecretsByProject(@NotNull String project) throws SystemException;

    //TODO move to separated interface
    /**
     * Retrieve the project secret values for the specified names of the project
     * @param id
     * @return
     */
    Map.Entry<String, String> getSecretData(@NotNull String id) throws SystemException;

    /**
     * Retrieve the project secret values for the specified names of the project
     * @param project
     * @param names
     * @return
     */
    Map<String, String> getSecretData(@NotNull String project, @NotNull Set<String> names) throws SystemException;

    /**
     * Store the values for the project secrets.
     * @param id
     * @param value
     */
    void storeSecretData(@NotNull String id, @NotNull String value) throws SystemException;

    /**
     * Store the values for the project secrets. If the secret does not exist, it will be created.
     * @param project
     * @param values
     */
    void storeSecretData(@NotNull String project, @NotNull Map<String, String> values) throws SystemException;
    // /**
    //  * Group the specifiedsecrets by secret name as stored in provider.
    //  * Only Kubernetes provider is supported at this moment.
    //  * @param projectId
    //  * @param secrets
    //  * @return
    //  */
    // //TODO move to runtimes, this logic is outside the service
    // @Deprecated(forRemoval = true)
    // Map<String, Set<String>> groupSecrets(String projectId, Collection<String> secrets) throws SystemException;
}
