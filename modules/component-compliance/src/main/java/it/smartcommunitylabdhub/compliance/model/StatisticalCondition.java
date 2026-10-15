package it.smartcommunitylabdhub.compliance.model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Condition asserting a metric satisfies a statistical hypothesis test. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StatisticalCondition {

    @Schema(title = "fields.compliance.objective.condition.statistical.test.title", description = "fields.compliance.objective.condition.statistical.test.description")
    private String test;
    @Schema(title = "fields.compliance.objective.condition.statistical.significance.title", description = "fields.compliance.objective.condition.statistical.significance.description")
    private Double significance;
    @Schema(title = "fields.compliance.objective.condition.statistical.hypothesis.title", description = "fields.compliance.objective.condition.statistical.hypothesis.description")
    private String hypothesis;
}
