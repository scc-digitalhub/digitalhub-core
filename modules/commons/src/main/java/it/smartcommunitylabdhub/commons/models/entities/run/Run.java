package it.smartcommunitylabdhub.commons.models.entities.run;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import it.smartcommunitylabdhub.commons.annotations.validators.ValidateField;
import it.smartcommunitylabdhub.commons.models.base.BaseEntity;
import it.smartcommunitylabdhub.commons.models.entities.run.metadata.RunMetadata;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@JsonPropertyOrder(alphabetic = true)
public class Run implements BaseEntity {

    @ValidateField(allowNull = true, fieldType = "uuid", message = "Invalid UUID4 string")
    private String id;

    @NotNull
    @ValidateField
    private String project;

    @NotNull
    @ValidateField
    private String kind;

    @Builder.Default
    private RunMetadata metadata = new RunMetadata();

    @Builder.Default
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Object> spec = new HashMap<>();

    @Builder.Default
    @JsonIgnore
    private Map<String, Object> extra = new HashMap<>();

    @Builder.Default
    private Map<String, Object> status = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getExtra() {
        return this.extra;
    }

    @JsonAnySetter
    public void setExtra(String key, Object value) {
        if (value != null) {
            extra.put(key, value);
        }
    }
}