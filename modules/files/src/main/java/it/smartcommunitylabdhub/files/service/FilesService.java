package it.smartcommunitylabdhub.files.service;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.io.InputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
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

    public @Nullable String getDownloadAsUrl(@NotNull String path) {
        Assert.hasText(path, "path can not be null or empty");

        log.debug("resolve store for {}", path);
        //try resolving path via stores
        FilesStore store = getStore(path);
        if (store == null) {
            log.debug("no store found.");
            return null;
        }

        log.debug("found store {}", store.getClass().getName());

        String url = store.downloadAsUrl(path);

        if (log.isTraceEnabled()) {
            log.trace("path resolved to download url {}", url);
        }

        return url;
    }

    public @Nullable InputStream getDownloadAsStream(@NotNull String path) {
        throw new UnsupportedOperationException();
    }
    
    public Map<String, Serializable> getObjectMetadata(@NotNull String path) {
        Assert.hasText(path, "path can not be null or empty");

        log.debug("resolve store for {}", path);
        //try resolving path via stores
        FilesStore store = getStore(path);
        if (store == null) {
            log.debug("no store found.");
            return null;
        }

        log.debug("found store {}", store.getClass().getName());

        Map<String,Serializable> metadata = store.readMetadata(path);

        if (log.isTraceEnabled()) {
            log.trace("path resolved to metadata {}", metadata);
        }

        return metadata;    	
    }
}