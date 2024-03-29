package it.smartcommunitylabdhub.commons.accessors.fields;

import io.micrometer.common.lang.Nullable;
import it.smartcommunitylabdhub.commons.accessors.Accessor;
import java.io.Serializable;
import java.util.Map;

/**
 * Status field common accessor
 */
public interface StatusFieldAccessor extends Accessor<Serializable> {
    default @Nullable String getState() {
        return get("state");
    }

    static StatusFieldAccessor with(Map<String, Serializable> map) {
        return () -> map;
    }
}
