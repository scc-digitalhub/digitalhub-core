package it.smartcommunitylabdhub.compliance.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
/**
 * Quantitative or qualitative measure of a defined notion. Primitive metrics can be combined into
 * composite metrics via {@link MetricComposition}.
 */
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class Metric extends BaseComplianceObject {

    @Schema(title = "fields.compliance.metric.kind.title", description = "fields.compliance.metric.kind.description")
    private MetricKind kind;
    @Schema(title = "fields.compliance.metric.scale.title", description = "fields.compliance.metric.scale.description")
    private MeasurementScale scale;
    @Schema(title = "fields.compliance.metric.unit.title", description = "fields.compliance.metric.unit.description")
    private UnitOfMeasure unit;
    /** Valid value domain. */
    @Schema(title = "fields.compliance.metric.range.title", description = "fields.compliance.metric.range.description")
    private ValueRange range;
    /** Populated for composite metrics. */
    @Schema(title = "fields.compliance.metric.composition.title", description = "fields.compliance.metric.composition.description")
    private MetricComposition composition;

    public enum MetricKind {
        PRIMITIVE,
        COMPOSITE,
    }

    public enum MeasurementScale {
        NOMINAL,
        ORDINAL,
        INTERVAL,
        RATIO,
        BOOLEAN,
    }
}
