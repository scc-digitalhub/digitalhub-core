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

package it.smartcommunitylabdhub.authorization.services;

import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/*
 * Helper service to aid authorization management over entities.
 * Could leverage cache on non-updatable fields
 */
public interface AuthorizableAwareEntityService<T extends BaseDTO> {
    /**
     * Find identifiers of entities via createdBy
     * @param createdBy
     * @return
     */
    List<String> findIdsByCreatedBy(@NotNull String createdBy);

    /**
     * Find identifiers of entities via updatedBy
     * @param updatedBy
     * @return
     */
    List<String> findIdsByUpdatedBy(@NotNull String updatedBy);

    /**
     * Find identifiers of entities via project
     * @param project
     * @return
     */
    List<String> findIdsByProject(@NotNull String project);

    /**
     * Find identifiers of entities via share
     * @param createdBy
     * @return
     */
    List<String> findIdsBySharedTo(@NotNull String user);

    /**
     * Find names of entities via createdBy
     * @param createdBy
     * @return
     */
    List<String> findNamesByCreatedBy(@NotNull String createdBy);

    /**
     * Find names of entities via updatedBy
     * @param updatedBy
     * @return
     */
    List<String> findNamesByUpdatedBy(@NotNull String updatedBy);

    /**
     * Find names of entities via project
     * @param project
     * @return
     */
    List<String> findNamesByProject(@NotNull String project);

    /**
     * Find names of entities via share
     * @param createdBy
     * @return
     */
    List<String> findNamesBySharedTo(@NotNull String user);
}
