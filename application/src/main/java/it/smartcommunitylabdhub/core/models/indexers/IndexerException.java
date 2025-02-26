package it.smartcommunitylabdhub.core.models.indexers;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;

public class IndexerException extends StoreException {

    public IndexerException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public IndexerException(String msg) {
        super(msg);
    }
}
