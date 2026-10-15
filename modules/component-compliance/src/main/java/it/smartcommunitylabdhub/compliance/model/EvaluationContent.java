package it.smartcommunitylabdhub.compliance.model;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Report content recording metric evaluation results. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvaluationContent {

    @Schema(title = "fields.compliance.evaluation.metrics.title", description = "fields.compliance.evaluation.metrics.description")
    private List<MetricResult> metrics;

    @Schema(title = "fields.compliance.evaluation.summary.title", description = "fields.compliance.evaluation.summary.description")
    private String summary;
}
