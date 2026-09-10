package it.smartcommunitylabdhub.compliance.model;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Valid value domain for a metric. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ValueRange {
    
    @Schema(title = "fields.compliance.value_range.lower.title", description = "fields.compliance.value_range.lower.description")
    /** Inclusive lower bound. */
    private Double lower;
    @Schema(title = "fields.compliance.value_range.upper.title", description = "fields.compliance.value_range.upper.description")
    /** Inclusive upper bound. */
    private Double upper;
    @Schema(title = "fields.compliance.value_range.discrete.title", description = "fields.compliance.value_range.discrete.description")
    /** Whether values are discrete. */
    private Boolean discrete;
    @Schema(title = "fields.compliance.value_range.categories.title", description = "fields.compliance.value_range.categories.description")
    /** Allowed category labels for nominal/ordinal scales. */
    private List<String> categories;
}
