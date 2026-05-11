package it.smartcommunitylabdhub.runtime.servicegraph.model.impl;

import it.smartcommunitylabdhub.runtime.servicegraph.model.OutputSpec;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@Builder
public class IgnoreSink implements OutputSpec {

    @Override
    public Map<String, java.io.Serializable> toMap() {
        return Map.of();
    }
}
