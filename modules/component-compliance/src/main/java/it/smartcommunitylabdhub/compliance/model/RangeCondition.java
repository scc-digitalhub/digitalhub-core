package it.smartcommunitylabdhub.compliance.model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Condition asserting a metric value falls within a numeric interval. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RangeCondition {

    @Schema(title = "fields.compliance.objective.condition.range.lower.title", description = "fields.compliance.objective.condition.range.lower.description")
    private Double lower;
    @Schema(title = "fields.compliance.objective.condition.range.upper.title", description = "fields.compliance.objective.condition.range.upper.description")
    private Double upper;
    @Schema(title = "fields.compliance.objective.condition.range.inclusive.title", description = "fields.compliance.objective.condition.range.inclusive.description")
    private Boolean inclusive;
}
