package it.smartcommunitylabdhub.runtime.servicegraph.model.impl;

import com.fasterxml.jackson.annotation.JsonProperty;

import it.smartcommunitylabdhub.runtime.servicegraph.model.NodeSpec;
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
public class HTTPService implements NodeSpec {

    @JsonProperty(value = "url", required = true)
    private String url;
    
    @JsonProperty(value = "method", defaultValue = "POST")
    private String method;
    
    @JsonProperty(value = "params")
    private Map<String, String> params;
    
    @JsonProperty(value = "headers")
    private Map<String, String> headers;
    
    @JsonProperty(value = "num_instances", defaultValue = "1")
    private Integer numInstances;
    
    @JsonProperty(value = "input_template")
    private String inputTemplate;
    
    @JsonProperty(value = "output_template")
    private String outputTemplate;

    @Override
    public Map<String, java.io.Serializable> toMap() {
        Map<String, java.io.Serializable> map = new HashMap<>();
        map.put("url", url);
        if (method != null) {
            map.put("method", method);
        }
        if (params != null) {
            map.put("params", new HashMap<>(params));
        }
        if (headers != null) {
            map.put("headers", new HashMap<>(headers));
        }
        if (numInstances != null) {
            map.put("num_instances", numInstances);
        }
        if (inputTemplate != null) {
            map.put("input_template", inputTemplate);
        }
        if (outputTemplate != null) {
            map.put("output_template", outputTemplate);
        }
        return map;
    }
}
