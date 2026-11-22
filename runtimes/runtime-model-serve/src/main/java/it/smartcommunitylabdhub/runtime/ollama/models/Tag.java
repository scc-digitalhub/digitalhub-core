package it.smartcommunitylabdhub.runtime.ollama.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class Tag {

    private String tag;
    private String digest;

    @JsonProperty("size_bytes")
    private Long sizeBytes;

    @JsonProperty("size_bytes_max")
    private Long sizeBytesMax;

    private Long context;
    private List<String> input;
    private Boolean cloud;

    @JsonProperty("updated_label")
    private String updatedLabel;
}
