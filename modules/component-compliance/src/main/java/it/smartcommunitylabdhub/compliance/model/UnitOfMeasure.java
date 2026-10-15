package it.smartcommunitylabdhub.compliance.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Physical unit of measurement for a metric value. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UnitOfMeasure {

    @Schema(title = "fields.compliance.unit_of_measure.symbol.title", description = "fields.compliance.unit_of_measure.symbol.description")
    private String symbol;
    @Schema(title = "fields.compliance.unit_of_measure.name.title", description = "fields.compliance.unit_of_measure.name.description")
    private String name;
}
