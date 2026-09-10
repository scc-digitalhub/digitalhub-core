package it.smartcommunitylabdhub.compliance.model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Condition asserting a metric value satisfies a comparison operator against a threshold. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ThresholdCondition {

    @Schema(title = "fields.compliance.objective.condition.threshold.operator.title", description = "fields.compliance.objective.condition.threshold.operator.description")
    private Operator operator;
    @Schema(title = "fields.compliance.objective.condition.threshold.value.title", description = "fields.compliance.objective.condition.threshold.value.description")
    private Double value;

    public enum Operator {
        GEQ,
        LEQ,
        GT,
        LT,
        EQ,
        NEQ,
    }
}
