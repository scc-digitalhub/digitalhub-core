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
public class FolderSink implements OutputSpec {

    @JsonProperty(value = "folder_path", required = true)
    private String folderPath;
    
    @JsonProperty(value = "filename_pattern", required = true)
    private String filenamePattern;

    @Override
    public Map<String, java.io.Serializable> toMap() {
        return Map.of(
            "folder_path", folderPath,
            "filename_pattern", filenamePattern
        );
    }
}
