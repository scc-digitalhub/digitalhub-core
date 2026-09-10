package it.smartcommunitylabdhub.compliance.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Confidence interval for a metric result. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConfidenceInterval {

    @Schema(title = "fields.compliance.confidence_interval.lower.title", description = "fields.compliance.confidence_interval.lower.description")
    private Double lower;
    @Schema(title = "fields.compliance.confidence_interval.upper.title", description = "fields.compliance.confidence_interval.upper.description")
    private Double upper;
    @Schema(title = "fields.compliance.confidence_interval.level.title", description = "fields.compliance.confidence_interval.level.description")
    /** Confidence level, e.g., 0.95. */
    private Double level;
}
