package it.smartcommunitylabdhub.commons.models.artifact;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.metadata.MetadataDTO;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.HashMap;
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
public class Artifact implements BaseDTO, MetadataDTO, SpecDTO, StatusDTO {

    @Nullable
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

    private String user;

    @Nullable
    @Builder.Default
    private Map<String, Serializable> metadata = new HashMap<>();

    @Nullable
    @Builder.Default
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Serializable> spec = new HashMap<>();

    @Nullable
    @Builder.Default
    private Map<String, Serializable> status = new HashMap<>();

    @Override
    public String getKey() {
        StringBuilder sb = new StringBuilder();
        sb.append(Keys.STORE_PREFIX).append(getProject());
        sb.append(Keys.PATH_DIVIDER).append(EntityName.ARTIFACT.getValue());
        sb.append(Keys.PATH_DIVIDER).append(getKind());
        sb.append(Keys.PATH_DIVIDER).append(getName());
        if (getId() != null) {
            sb.append(Keys.ID_DIVIDER).append(getId());
        }

        return sb.toString();
    }
}
