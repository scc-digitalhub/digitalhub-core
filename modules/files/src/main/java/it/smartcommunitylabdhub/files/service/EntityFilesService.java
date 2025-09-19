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

package it.smartcommunitylabdhub.files.service;

import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.files.models.DownloadInfo;
import it.smartcommunitylabdhub.files.models.FileInfo;
import it.smartcommunitylabdhub.files.models.UploadInfo;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public interface EntityFilesService<T extends BaseDTO> {
    public List<FileInfo> getFileInfo(@NotNull String id) throws NoSuchEntityException, SystemException;

    public void storeFileInfo(@NotNull String id, List<FileInfo> files) throws SystemException;

    public DownloadInfo downloadFileAsUrl(@NotNull String id) throws NoSuchEntityException, SystemException;

    public DownloadInfo downloadFileAsUrl(@NotNull String id, @NotNull String path)
        throws NoSuchEntityException, SystemException;

    public UploadInfo uploadFileAsUrl(
        @NotNull String project,
        @Nullable String name,
        @Nullable String id,
        @NotNull String filename
    ) throws NoSuchEntityException, SystemException;

    public UploadInfo startMultiPartUpload(
        @NotNull String project,
        @Nullable String name,
        @Nullable String id,
        @NotNull String filename
    ) throws NoSuchEntityException, SystemException;

    public UploadInfo uploadMultiPart(
        @NotNull String project,
        @Nullable String id,
        @NotNull String path,
        @NotNull String uploadId,
        @NotNull Integer partNumber
    ) throws NoSuchEntityException, SystemException;

    public UploadInfo completeMultiPartUpload(
        @NotNull String project,
        @Nullable String id,
        @NotNull String path,
        @NotNull String uploadId,
        @NotNull List<String> eTagPartList
    ) throws NoSuchEntityException, SystemException;
}
