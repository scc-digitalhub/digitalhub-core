package it.smartcommunitylabdhub.runtime.servicegraph.model;

import java.io.Serializable;
import java.util.Map;

public interface InputSpec {

    public Map<String, Serializable> toMap();
}
