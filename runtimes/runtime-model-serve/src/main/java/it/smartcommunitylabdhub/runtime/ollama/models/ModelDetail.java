package it.smartcommunitylabdhub.runtime.ollama.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** ModelDetail, including the inherited Model properties, from the Ollama Library API. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModelDetail {

    private String name;
    private String url;
    private String description;
    private List<String> capabilities;
    private List<String> sizes;
    private Boolean cloud;
    private Long pulls;

    @JsonProperty("pulls_label")
    private String pullsLabel;

    private Integer tags;

    @JsonProperty("updated_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private OffsetDateTime updatedAt;

    @JsonProperty("updated_label")
    private String updatedLabel;

    @JsonProperty("size_bytes")
    private Map<String, Long> sizeBytes;

    @JsonProperty("tag_details")
    private List<Tag> tagDetails;

    @JsonProperty("tags_fetched_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private OffsetDateTime tagsFetchedAt;

    private Boolean stale;

    @JsonProperty("refresh_error")
    private String refreshError;
}
