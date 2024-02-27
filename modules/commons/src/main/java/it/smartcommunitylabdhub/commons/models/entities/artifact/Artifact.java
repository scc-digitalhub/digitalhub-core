package it.smartcommunitylabdhub.commons.models.entities.artifact;

import com.fasterxml.jackson.annotation.*;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@JsonPropertyOrder(alphabetic = true)
public class Artifact implements BaseDTO {

    @Pattern(regexp = Keys.SLUG_PATTERN)
    private String id;

    @NotNull
    @Pattern(regexp = Keys.SLUG_PATTERN)
    private String name;

    @NotNull
    @Pattern(regexp = Keys.SLUG_PATTERN)
    private String kind;

    @NotNull
    @Pattern(regexp = Keys.SLUG_PATTERN)
    private String project;

    @Builder.Default
    private Map<String, Serializable> metadata = new HashMap<>();

    @Builder.Default
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Serializable> spec = new HashMap<>();

    @Builder.Default
    @JsonIgnore
    private Map<String, Serializable> extra = new HashMap<>();

    @Builder.Default
    private Map<String, Serializable> status = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Serializable> getExtra() {
        return this.extra;
    }

    @JsonAnySetter
    public void setExtra(String key, Serializable value) {
        if (value != null) {
            extra.put(key, value);
        }
    }
}
