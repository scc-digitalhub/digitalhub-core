package it.smartcommunitylabdhub.compliance.model;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Composition specification for a composite metric. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MetricComposition {

    @Schema(title = "fields.compliance.metric_composition.components.title", description = "fields.compliance.metric_composition.components.description")
    /** Constituent metrics. */
    private List<Metric> components;
    @Schema(title = "fields.compliance.metric_composition.aggregation.title", description = "fields.compliance.metric_composition.aggregation.description")
    private AggregationFunction aggregation;
    @Schema(title = "fields.compliance.metric_composition.custom_fn.title", description = "fields.compliance.metric_composition.custom_fn.description")
    /** Description for {@code OTHER} aggregation. */
    @JsonProperty("custom_fn")
    private String customFn;

    public enum AggregationFunction {
        MEAN,
        MIN,
        MAX,
        SUM,
        PRODUCT,
        OTHER,
    }
}
