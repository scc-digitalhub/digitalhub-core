package it.smartcommunitylabdhub.runtime.servicegraph.model.impl;

import com.fasterxml.jackson.annotation.JsonProperty;

import it.smartcommunitylabdhub.runtime.servicegraph.model.OutputSpec;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileSink implements OutputSpec {

    @JsonProperty(value = "file_name", required = true)
    private String fileName;

    @Override
    public Map<String, java.io.Serializable> toMap() {
        return Map.of("file_name", fileName);
    }
}
