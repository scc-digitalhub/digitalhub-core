package it.smartcommunitylabdhub.compliance.model;

import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Schema field descriptor used in tabular data and as model input/output. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FieldSpec {

    @Schema(title = "fields.compliance.field_spec.name.title", description = "fields.compliance.field_spec.name.description")
    private String name;
    @Schema(title = "fields.compliance.field_spec.dtype.title", description = "fields.compliance.field_spec.dtype.description")
    private DataType dtype;
    @Schema(title = "fields.compliance.field_spec.nullable.title", description = "fields.compliance.field_spec.nullable.description")
    private boolean nullable;
    @Schema(title = "fields.compliance.field_spec.description.title", description = "fields.compliance.field_spec.description.description")
    private String description;
    @Schema(title = "fields.compliance.field_spec.sensitivity.title", description = "fields.compliance.field_spec.sensitivity.description")
    private SensitivityLevel sensitivity;
    @Schema(title = "fields.compliance.field_spec.tags.title", description = "fields.compliance.field_spec.tags.description")
    private Set<String> tags;

    public enum DataType {
        INT,
        FLOAT,
        STRING,
        BOOL,
        STRUCT,
        ARRAY,
        MAP,
        BYTES,
        DATE,
        DATETIME,
        CUSTOM,
    }
}
