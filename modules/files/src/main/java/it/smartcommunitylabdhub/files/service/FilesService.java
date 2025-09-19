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
import it.smartcommunitylabdhub.commons.infrastructure.Configuration;
import it.smartcommunitylabdhub.commons.infrastructure.ConfigurationProvider;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.models.project.ProjectBaseSpec;
import it.smartcommunitylabdhub.files.http.HttpStore;
import it.smartcommunitylabdhub.files.models.DownloadInfo;
import it.smartcommunitylabdhub.files.models.FileInfo;
import it.smartcommunitylabdhub.files.models.UploadInfo;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 *
 * Path-aware files service with support for multiple backends
 */

@Service
@Slf4j
public class FilesService implements ConfigurationProvider, InitializingBean {

    private final Map<String, FilesStore> stores = new HashMap<>();

    @Value("${files.default.store}")
    private String defaultStore;

    private FilesConfig config;

    public FilesService() {
        //create an http read-only store
        HttpStore store = new HttpStore();

        //register by default
        registerStore("http://", store);
        registerStore("https://", store);
        registerStore("ftp://", store);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        //build config
        this.config = FilesConfig.builder().defaultFilesStore(defaultStore).build();
    }

    @Override
    public Configuration getConfig() {
        return config;
    }

    public void registerStore(String prefix, FilesStore store) {
        Assert.hasText(prefix, "prefix is required to match paths");
        Assert.notNull(store, "store can not be null");

        if (stores.containsKey(prefix)) {
            throw new IllegalArgumentException("prefix is already handled by another store");
        }

        //register
        log.debug("register store for {}: {}", prefix, store.getClass().getName());
        stores.put(prefix, store);
    }

    public Map<String, FilesStore> getStores() {
        return this.stores;
    }

    //TODO refactor
    public String getDefaultStore(@Nullable Project project) {
        //define base store, prefer any s3 with bucket if available
        List<String> keys = stores.keySet().stream().filter(k -> k.startsWith("s3://")).collect(Collectors.toList());

        Optional<String> dk = keys.stream().filter(k -> !k.equals("s3://")).findFirst();
        Optional<String> df = keys.stream().filter(k -> k.equals("s3://")).findFirst();

        String baseStore = dk.isPresent() ? dk.get() : (df.isPresent() ? df.get() : null);
        String store = StringUtils.hasText(defaultStore) ? defaultStore : baseStore;

        if (project != null) {
            //check if project has a configured store
            ProjectBaseSpec spec = new ProjectBaseSpec();
            spec.configure(project.getSpec());

            if (spec.getConfig() != null && StringUtils.hasText(spec.getConfig().getDefaultFilesStore())) {
                //use project store as default
                store = spec.getConfig().getDefaultFilesStore();
            }
        }

        return store;
    }

    public @Nullable FilesStore getStore(@NotNull String path) {
        //match longest prefix in keys
        int count = 0;
        FilesStore match = null;
        for (Map.Entry<String, FilesStore> entry : stores.entrySet()) {
            if (path.startsWith(entry.getKey()) && entry.getKey().length() > count) {
                count = entry.getKey().length();
                match = entry.getValue();
            }
        }

        return match;
    }

    public @Nullable DownloadInfo getDownloadAsUrl(
        @NotNull String path,
        @Nullable Integer duration,
        @Nullable List<Credentials> credentials
    ) throws StoreException {
        Assert.hasText(path, "path can not be null or empty");

        log.debug("resolve store for {}", path);
        //try resolving path via stores
        FilesStore store = getStore(path);
        if (store == null) {
            log.debug("no store found.");
            return null;
        }

        log.debug("found store {}", store.getClass().getName());

        DownloadInfo info = store.downloadAsUrl(path, duration, credentials);

        if (log.isTraceEnabled()) {
            log.trace("path resolved to download {}", info);
        }

        return info;
    }

    public @Nullable InputStream getDownloadAsStream(@NotNull String path, @Nullable List<Credentials> credentials)
        throws StoreException {
        throw new UnsupportedOperationException();
    }

    public List<FileInfo> getFileInfo(@NotNull String path, @Nullable List<Credentials> credentials)
        throws StoreException {
        Assert.hasText(path, "path can not be null or empty");

        log.debug("resolve store for {}", path);
        //try resolving path via stores
        FilesStore store = getStore(path);
        if (store == null) {
            log.debug("no store found.");
            return null;
        }

        log.debug("found store {}", store.getClass().getName());

        List<FileInfo> metadata = store.fileInfo(path, true, credentials);

        if (log.isTraceEnabled()) {
            log.trace("path resolved to metadata {}", metadata);
        }

        return metadata;
    }

    public @Nullable UploadInfo getUploadAsUrl(@NotNull String path, @Nullable List<Credentials> credentials)
        throws StoreException {
        Assert.hasText(path, "path can not be null or empty");

        log.debug("resolve store for {}", path);
        //try resolving path via stores
        FilesStore store = getStore(path);
        if (store == null) {
            log.debug("no store found.");
            return null;
        }

        log.debug("found store {}", store.getClass().getName());

        UploadInfo info = store.uploadAsUrl(path, credentials);

        if (log.isTraceEnabled()) {
            log.trace("path resolved to upload {}", info);
        }

        return info;
    }

    public @Nullable UploadInfo startMultiPartUpload(@NotNull String path, @Nullable List<Credentials> credentials)
        throws StoreException {
        Assert.hasText(path, "path can not be null or empty");

        log.debug("resolve store for {}", path);
        //try resolving path via stores
        FilesStore store = getStore(path);
        if (store == null) {
            log.debug("no store found.");
            return null;
        }

        log.debug("found store {}", store.getClass().getName());

        UploadInfo info = store.startMultiPartUpload(path, credentials);

        if (log.isTraceEnabled()) {
            log.trace("path resolved to multi-part upload {}", info);
        }

        return info;
    }

    public @Nullable UploadInfo uploadMultiPart(
        @NotNull String path,
        @NotNull String uploadId,
        @NotNull Integer partNumber,
        @Nullable List<Credentials> credentials
    ) throws StoreException {
        Assert.hasText(path, "path can not be null or empty");
        Assert.hasText(uploadId, "uploadId can not be null or empty");
        Assert.notNull(partNumber, "partNumber can not be null or empty");

        log.debug("resolve store for {}", path);
        //try resolving path via stores
        FilesStore store = getStore(path);
        if (store == null) {
            log.debug("no store found.");
            return null;
        }

        log.debug("found store {}", store.getClass().getName());

        UploadInfo info = store.uploadMultiPart(path, uploadId, partNumber, credentials);

        if (log.isTraceEnabled()) {
            log.trace("path resolved to part upload {}", info);
        }

        return info;
    }

    public @Nullable UploadInfo completeMultiPartUpload(
        @NotNull String path,
        @NotNull String uploadId,
        @NotNull List<String> partList,
        @Nullable List<Credentials> credentials
    ) throws StoreException {
        Assert.hasText(path, "path can not be null or empty");
        Assert.hasText(uploadId, "uploadId can not be null or empty");
        Assert.notNull(partList, "partList can not be null or empty");

        log.debug("resolve store for {}", path);
        //try resolving path via stores
        FilesStore store = getStore(path);
        if (store == null) {
            log.debug("no store found.");
            return null;
        }

        log.debug("found store {}", store.getClass().getName());

        UploadInfo info = store.completeMultiPartUpload(path, uploadId, partList, credentials);

        if (log.isTraceEnabled()) {
            log.trace("path resolved to complete upload {}", info);
        }

        return info;
    }

    public void remove(@NotNull String path, @Nullable List<Credentials> credentials) throws StoreException {
        FilesStore store = getStore(path);
        if (store != null) {
            log.debug("found store {}", store.getClass().getName());

            store.remove(path, true, credentials);
            log.debug("remove path {}", path);
        } else {
            log.debug("no store found.");
        }
    }
}
