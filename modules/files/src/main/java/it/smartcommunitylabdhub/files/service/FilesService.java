package it.smartcommunitylabdhub.files.service;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.files.FileInfo;
import it.smartcommunitylabdhub.files.http.HttpStore;
import it.smartcommunitylabdhub.files.models.DownloadInfo;
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
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 *
 * Path-aware files service with support for multiple backends
 */

@Service
@Slf4j
public class FilesService {

    private final Map<String, FilesStore> stores = new HashMap<>();

    public FilesService() {
        //create an http read-only store
        HttpStore store = new HttpStore();

        //register by default
        registerStore("http://", store);
        registerStore("https://", store);
        registerStore("ftp://", store);
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

    //TODO refactor
    public String getDefaultStore() {
        //select configured s3:// by default

        List<String> keys = stores.keySet().stream().filter(k -> k.startsWith("s3://")).collect(Collectors.toList());

        Optional<String> dk = keys.stream().filter(k -> !k.equals("s3://")).findFirst();
        Optional<String> df = keys.stream().filter(k -> k.equals("s3://")).findFirst();

        return dk.isPresent() ? dk.get() : (df.isPresent() ? df.get() : null);
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

    public @Nullable DownloadInfo getDownloadAsUrl(@NotNull String path, UserAuthentication<?> auth)
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

        DownloadInfo info = store.downloadAsUrl(path, auth);

        if (log.isTraceEnabled()) {
            log.trace("path resolved to download {}", info);
        }

        return info;
    }

    public @Nullable InputStream getDownloadAsStream(@NotNull String path, UserAuthentication<?> auth)
        throws StoreException {
        throw new UnsupportedOperationException();
    }

    public List<FileInfo> getFileInfo(@NotNull String path, UserAuthentication<?> auth) throws StoreException {
        Assert.hasText(path, "path can not be null or empty");

        log.debug("resolve store for {}", path);
        //try resolving path via stores
        FilesStore store = getStore(path);
        if (store == null) {
            log.debug("no store found.");
            return null;
        }

        log.debug("found store {}", store.getClass().getName());

        List<FileInfo> metadata = store.fileInfo(path, auth);

        if (log.isTraceEnabled()) {
            log.trace("path resolved to metadata {}", metadata);
        }

        return metadata;
    }

    public @Nullable UploadInfo getUploadAsUrl(@NotNull String path, UserAuthentication<?> auth) throws StoreException {
        Assert.hasText(path, "path can not be null or empty");

        log.debug("resolve store for {}", path);
        //try resolving path via stores
        FilesStore store = getStore(path);
        if (store == null) {
            log.debug("no store found.");
            return null;
        }

        log.debug("found store {}", store.getClass().getName());

        UploadInfo info = store.uploadAsUrl(path, auth);

        if (log.isTraceEnabled()) {
            log.trace("path resolved to upload {}", info);
        }

        return info;
    }

    public @Nullable UploadInfo startMultiPartUpload(@NotNull String path, UserAuthentication<?> auth)
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

        UploadInfo info = store.startMultiPartUpload(path, auth);

        if (log.isTraceEnabled()) {
            log.trace("path resolved to multi-part upload {}", info);
        }

        return info;
    }

    public @Nullable UploadInfo uploadMultiPart(
        @NotNull String path,
        @NotNull String uploadId,
        @NotNull Integer partNumber,
        UserAuthentication<?> auth
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

        UploadInfo info = store.uploadMultiPart(path, uploadId, partNumber, auth);

        if (log.isTraceEnabled()) {
            log.trace("path resolved to part upload {}", info);
        }

        return info;
    }

    public @Nullable UploadInfo completeMultiPartUpload(
        @NotNull String path,
        @NotNull String uploadId,
        @NotNull List<String> partList,
        UserAuthentication<?> auth
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

        UploadInfo info = store.completeMultiPartUpload(path, uploadId, partList, auth);

        if (log.isTraceEnabled()) {
            log.trace("path resolved to complete upload {}", info);
        }

        return info;
    }

    public void remove(@NotNull String path, UserAuthentication<?> auth) throws StoreException {
        FilesStore store = getStore(path);
        if (store != null) {
            log.debug("found store {}", store.getClass().getName());
            store.remove(path, auth);
            log.debug("remove path {}", path);
        } else {
            log.debug("no store found.");
        }
    }
}
