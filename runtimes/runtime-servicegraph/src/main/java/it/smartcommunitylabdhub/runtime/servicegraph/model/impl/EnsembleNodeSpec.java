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
public class EnsembleNodeSpec implements NodeSpec {

    @JsonProperty(value = "merge_mode", defaultValue = "concat")
    private String mergeMode;
    
    @JsonProperty(value = "template")
    private String template;

    @Override
    public Map<String, java.io.Serializable> toMap() {
        Map<String, java.io.Serializable> map = new HashMap<>();
        if (mergeMode != null) {
            map.put("merge_mode", mergeMode);
        }
        if (template != null) {
            map.put("template", template);
        }
        return map;
    }
}
