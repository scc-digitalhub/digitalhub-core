package it.smartcommunitylabdhub.runtime.huggingface.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HuggingfaceMetadata {

    @JsonProperty("model")
    private Map<String, Serializable> model;

    @JsonProperty("model_card")
    private String modelCard;

    @JsonProperty("config")
    private Map<String, Serializable> config;

    @JsonProperty("model_id")
    private String modelId;

    @JsonProperty("fetched_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private OffsetDateTime fetchedAt;
}
