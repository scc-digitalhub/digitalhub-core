package it.smartcommunitylabdhub.runtime.servicegraph.model;

import java.io.Serializable;
import java.util.Map;

public interface NodeSpec {

    Map<String, Serializable> toMap();
}
