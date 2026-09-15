package it.smartcommunitylabdhub.projects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.metadata.MetadataDTO;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.extensions.model.ExtensibleDTO;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.Nullable;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
@JsonPropertyOrder(alphabetic = true)
public class Project implements ExtensibleDTO, BaseDTO, MetadataDTO, SpecDTO, StatusDTO {

    @Nullable
    @Pattern(regexp = Keys.RFC_1123_PATTERN)
    @Size(min = 2, max = Keys.MAX_NAME_LENGTH)
    private String id;

    @NotNull
    @Pattern(regexp = Keys.RFC_1123_PATTERN)
    @Size(min = 2, max = Keys.MAX_NAME_LENGTH)
    private String name;

    @Pattern(regexp = Keys.SLUG_PATTERN)
    private final String kind = "project";

    private String user;

    @Builder.Default
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Serializable> spec = new HashMap<>();

    @Builder.Default
    private Map<String, Serializable> status = new HashMap<>();

    @Builder.Default
    private Map<String, Serializable> metadata = new HashMap<>();

    @Nullable
    @Builder.Default
    private List<Map<String, Serializable>> extensions = new LinkedList<>();

    @Override
    public String getProject() {
        return name;
    }

    @Override
    public String getKey() {
        return (Keys.STORE_PREFIX + getProject());
    }
}
