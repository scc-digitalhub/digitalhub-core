package it.smartcommunitylabdhub.runtime.servicegraph.model.impl;

import com.fasterxml.jackson.annotation.JsonProperty;

import it.smartcommunitylabdhub.runtime.servicegraph.model.InputSpec;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MJPEGSource implements InputSpec {

    @JsonProperty(value = "url", required = true)
    private String url;
    
    @JsonProperty(value = "port", defaultValue = "8080")
    private Integer port;
    
    @JsonProperty(value = "capacity", defaultValue = "10")
    private Integer capacity;

    @Override
    public Map<String, java.io.Serializable> toMap() {
        Map<String, java.io.Serializable> map = new HashMap<>();
        map.put("url", url);
        if (port != null) {
            map.put("port", port);
        }
        if (capacity != null) {
            map.put("capacity", capacity);
        }
        return map;
    }
}
