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

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.models.files.FileInfo;
import it.smartcommunitylabdhub.files.models.DownloadInfo;
import it.smartcommunitylabdhub.files.models.UploadInfo;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public interface FilesStore {
    DownloadInfo downloadAsUrl(@NotNull String path, @Nullable List<Credentials> credentials) throws StoreException;

    UploadInfo uploadAsUrl(@NotNull String path, @Nullable List<Credentials> credentials) throws StoreException;

    UploadInfo startMultiPartUpload(@NotNull String path, @Nullable List<Credentials> credentials)
        throws StoreException;

    UploadInfo uploadMultiPart(
        @NotNull String path,
        @NotNull String uploadId,
        @NotNull Integer partNumber,
        @Nullable List<Credentials> credentials
    ) throws StoreException;

    UploadInfo completeMultiPartUpload(
        @NotNull String path,
        @NotNull String uploadId,
        @NotNull List<String> partList,
        @Nullable List<Credentials> credentials
    ) throws StoreException;

    //TODO
    // InputStream downloadAsStream(@NotNull String path);

    List<FileInfo> fileInfo(@NotNull String path, @Nullable List<Credentials> credentials) throws StoreException;

    void remove(@NotNull String path, @Nullable List<Credentials> credentials) throws StoreException;
}
