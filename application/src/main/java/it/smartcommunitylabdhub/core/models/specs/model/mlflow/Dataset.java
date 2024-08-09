package it.smartcommunitylabdhub.core.models.specs.model.mlflow;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Dataset {

    private String name;
    private String digest;
    private String profile;
    private String schema;
    private String source;
    @JsonProperty("source_type")
    private String sourceType;
}