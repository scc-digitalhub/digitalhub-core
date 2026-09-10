package it.smartcommunitylabdhub.compliance.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Observed value of a single metric within a report. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MetricResult {

    @Schema(title = "fields.compliance.metric_result.metric.title", description = "fields.compliance.metric_result.metric.description")
    private Metric metric;
    @Schema(title = "fields.compliance.metric_result.value.title", description = "fields.compliance.metric_result.value.description")
    /** Number, String, Boolean, or structured map value. */
    private Object value;
    @Schema(title = "fields.compliance.metric_result.confidence.title", description = "fields.compliance.metric_result.confidence.description")
    private ConfidenceInterval confidence;
    @Schema(title = "fields.compliance.metric_result.computed_at.title", description = "fields.compliance.metric_result.computed_at.description")
    @JsonProperty("computed_at")
    private Instant computedAt;
}
