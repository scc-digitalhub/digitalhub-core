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

package it.smartcommunitylabdhub.extensions;

import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.extensions.model.Extension;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.BindException;

public interface ExtensionManager {
    //all
    Page<Extension> listExtensions(Pageable pageable) throws SystemException;

    //by entity
    List<Extension> listExtensionsByEntity(String entity) throws SystemException;
    Page<Extension> listExtensionsByEntity(String entity, Pageable pageable) throws SystemException;

    //by project
    List<Extension> listExtensionsByProject(String project) throws SystemException;
    Page<Extension> listExtensionsByProject(String project, Pageable pageable) throws SystemException;

    List<Extension> listExtensionsByProject(String project, String kind) throws SystemException;
    Page<Extension> listExtensionsByProject(String project, String kind, Pageable pageable) throws SystemException;
    //by user
    List<Extension> listExtensionsByUser(String user) throws SystemException;
    Page<Extension> listExtensionsByUser(String user, Pageable pageable) throws SystemException;

    //by parent
    List<Extension> listExtensionsByParent(String parent) throws SystemException;
    List<Extension> listExtensionsByParent(String parent, String kind) throws SystemException;
    List<Extension> listExtensionsByParent(String parent, String kind, String name) throws SystemException;

    Extension findExtension(String id) throws SystemException;
    Extension getExtension(String id) throws NoSuchEntityException, SystemException;

    Extension createExtension(Extension extension)
        throws DuplicatedEntityException, BindException, IllegalArgumentException, SystemException;
    Extension createOrUpdateExtension(Extension extension)
        throws BindException, IllegalArgumentException, SystemException;
    Extension updateExtension(String id, Extension extension)
        throws NoSuchEntityException, BindException, IllegalArgumentException, SystemException;

    void deleteExtension(String id) throws SystemException;
    void deleteExtensionsByParent(String parent) throws SystemException;
    void deleteExtensionsByProject(String project) throws SystemException;

    void deleteExtensionByEntity(@NotNull String entity) throws StoreException;

    void deleteExtensionByEntityAndUser(@NotNull String entity, @NotNull String user) throws StoreException;
    void deleteExtensionByEntityAndProject(@NotNull String entity, @NotNull String project) throws StoreException;
    void deleteExtensionByEntityAndKind(@NotNull String entity, @NotNull String kind) throws StoreException;
}
