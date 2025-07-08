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
import it.smartcommunitylabdhub.commons.models.dataitem.DataItem;
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
public interface DataItemManager {
    /**
     * List all dataItems
     * @param pageable
     * @return
     */
    Page<DataItem> listDataItems(Pageable pageable) throws SystemException;

    /**
     * List the latest version of every dataItem
     * @return
     */
    List<DataItem> listLatestDataItems() throws SystemException;

    /**
     * List the latest version of every dataItem
     * @param pageable
     * @return
     */
    Page<DataItem> listLatestDataItems(Pageable pageable) throws SystemException;

    /**
     * List all versions of every dataItem for a user
     * @param user
     * @return
     */
    List<DataItem> listDataItemsByUser(@NotNull String user) throws SystemException;

    /**
     * List all versions of every dataItem for a project
     * @param project
     * @return
     */
    List<DataItem> listDataItemsByProject(@NotNull String project) throws SystemException;

    /**
     * List all versions of every dataItem for a project
     * @param project
     * @param pageable
     * @return
     */
    Page<DataItem> listDataItemsByProject(@NotNull String project, Pageable pageable) throws SystemException;

    /**
     * List the latest version of every dataItem for a project
     * @param project
     * @return
     */
    List<DataItem> listLatestDataItemsByProject(@NotNull String project) throws SystemException;

    /**
     * List the latest version of every dataItem for a project
     * @param project
     * @param pageable
     * @return
     */
    Page<DataItem> listLatestDataItemsByProject(@NotNull String project, Pageable pageable) throws SystemException;

    /**
     * List all dataItems, with optional filters
     * @param pageable
     * @param filter
     * @return
     */
    Page<DataItem> searchDataItems(Pageable pageable, @Nullable SearchFilter<DataItem> filter) throws SystemException;

    /**
     * List the latest version of every dataItem, with optional filters
     * @param pageable
     * @param filter
     * @return
     */
    Page<DataItem> searchLatestDataItems(Pageable pageable, @Nullable SearchFilter<DataItem> filter)
        throws SystemException;

    /**
     * List all versions of every dataItem, with optional filters
     * @param project
     * @param pageable
     * @param filter
     * @return
     */
    Page<DataItem> searchDataItemsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<DataItem> filter
    ) throws SystemException;

    /**
     * List the latest version of every dataItem, with optional filters
     * @param project
     * @param pageable
     * @param filter
     * @return
     */
    Page<DataItem> searchLatestDataItemsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<DataItem> filter
    ) throws SystemException;

    /**
     * Find all versions of a given dataItem
     * @param project
     * @param name
     * @return
     */
    List<DataItem> findDataItems(@NotNull String project, @NotNull String name) throws SystemException;

    /**
     * Find all versions of a given dataItem
     * @param project
     * @param name
     * @param pageable
     * @return
     */
    Page<DataItem> findDataItems(@NotNull String project, @NotNull String name, Pageable pageable)
        throws SystemException;

    /**
     * Find a specific dataItem (version) via unique ID. Returns null if not found
     * @param id
     * @return
     */
    @Nullable
    DataItem findDataItem(@NotNull String id) throws SystemException;

    /**
     * Get a specific dataItem (version) via unique ID. Throws exception if not found
     * @param id
     * @return
     * @throws NoSuchEntityException
     */
    DataItem getDataItem(@NotNull String id) throws NoSuchEntityException, SystemException;

    /**
     * Get the latest version of a given dataItem
     * @param project
     * @param name
     * @return
     * @throws NoSuchEntityException
     */
    DataItem getLatestDataItem(@NotNull String project, @NotNull String name)
        throws NoSuchEntityException, SystemException;

    /**
     * Create a new dataItem and store it
     * @param dataItemDTO
     * @return
     * @throws DuplicatedEntityException
     */
    DataItem createDataItem(@NotNull DataItem dataItemDTO)
        throws DuplicatedEntityException, BindException, IllegalArgumentException, SystemException;

    /**
     * Update a specific dataItem version
     * @param id
     * @param dataItemDTO
     * @return
     * @throws NoSuchEntityException
     */
    DataItem updateDataItem(@NotNull String id, @NotNull DataItem dataItemDTO)
        throws NoSuchEntityException, BindException, IllegalArgumentException, SystemException;

    /**
     * Delete a specific dataItem (version) via unique ID
     * @param id
     */
    void deleteDataItem(@NotNull String id, @Nullable Boolean cascade) throws SystemException;

    /**
     * Delete all versions of a given dataItem
     * @param project
     * @param name
     */
    void deleteDataItems(@NotNull String project, @NotNull String name, @Nullable Boolean cascade)
        throws SystemException;

    /**
     * Delete all dataItems for a given project, with cascade.
     * @param project
     */
    void deleteDataItemsByProject(@NotNull String project, @Nullable Boolean cascade) throws SystemException;
}
