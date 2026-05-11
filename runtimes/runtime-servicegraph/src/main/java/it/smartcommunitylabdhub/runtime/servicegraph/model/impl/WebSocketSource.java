package it.smartcommunitylabdhub.runtime.servicegraph.model.impl;

import com.fasterxml.jackson.annotation.JsonProperty;

import it.smartcommunitylabdhub.runtime.servicegraph.model.InputSpec;

public class WebSocketSource implements InputSpec {

    @JsonProperty(value = "port", defaultValue = "8080")
    private Integer port;   
    @JsonProperty(value = "capacity", defaultValue = "10")
    private Integer capacity;


     @Override
    public java.util.Map<String, java.io.Serializable> toMap() {
        return java.util.Map.of(
            "port", port,
            "capacity", capacity
        );
    }

}
