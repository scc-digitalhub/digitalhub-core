package it.smartcommunitylabdhub.compliance.model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
/**
 * Discriminated union for compliance objective conditions. Implementations:
 * {@link ThresholdCondition}, {@link RangeCondition}, {@link CategoricalCondition},
 * {@link StatisticalCondition}, {@link CompositeCondition}.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ObjectiveCondition {

    @Schema(title = "fields.compliance.objective.condition.threshold.title", description = "fields.compliance.objective.condition.threshold.description")
    private ThresholdCondition threshold;
    @Schema(title = "fields.compliance.objective.condition.range.title", description = "fields.compliance.objective.condition.range.description")
    private RangeCondition range;
    @Schema(title = "fields.compliance.objective.condition.categorical.title", description = "fields.compliance.objective.condition.categorical.description")
    private CategoricalCondition categorical;
    @Schema(title = "fields.compliance.objective.condition.statistical.title", description = "fields.compliance.objective.condition.statistical.description")
    private StatisticalCondition statistical;
    @Schema(title = "fields.compliance.objective.condition.composite.title", description = "fields.compliance.objective.condition.composite.description")
    private CompositeCondition composite;
}
