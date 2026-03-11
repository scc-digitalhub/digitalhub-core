package it.smartcommunitylabdhub.runtime.servicegraph.model.impl;

import it.smartcommunitylabdhub.runtime.servicegraph.model.NodeSpec;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@Builder
public class SwitchNodeSpec implements NodeSpec {

    @Override
    public Map<String, java.io.Serializable> toMap() {
        return Map.of();
    }
}
