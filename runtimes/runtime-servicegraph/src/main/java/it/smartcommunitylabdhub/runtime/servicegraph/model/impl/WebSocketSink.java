package it.smartcommunitylabdhub.runtime.servicegraph.model.impl;

import com.fasterxml.jackson.annotation.JsonProperty;

import it.smartcommunitylabdhub.runtime.servicegraph.model.OutputSpec;
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
public class WebSocketSink implements OutputSpec {

    @JsonProperty(value = "url", required = true)
    private String url;
    
    @JsonProperty(value = "msg_type", defaultValue = "1")
    private Integer msgType;
    
    @JsonProperty(value = "params")
    private Map<String, String> params;
    
    @JsonProperty(value = "headers")
    private Map<String, String> headers;

    @Override
    public Map<String, java.io.Serializable> toMap() {
        Map<String, java.io.Serializable> map = new HashMap<>();
        map.put("url", url);
        if (msgType != null) {
            map.put("msg_type", msgType);
        }
        if (params != null) {
            map.put("params", new HashMap<>(params));
        }
        if (headers != null) {
            map.put("headers", new HashMap<>(headers));
        }
        return map;
    }
}
