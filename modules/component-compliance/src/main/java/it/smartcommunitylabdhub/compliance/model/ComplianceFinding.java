package it.smartcommunitylabdhub.compliance.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A non-conformance finding produced during a compliance assessment. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({"objective", "description", "report"})
public class ComplianceFinding {

    @Schema(title = "fields.compliance.finding.description.title", description = "fields.compliance.finding.description.description")
    private String description;
    
    @Schema(title = "fields.compliance.finding.objective.title", description = "fields.compliance.finding.objective.description")
    private String objective;
    @Schema(title = "fields.compliance.finding.report.title", description = "fields.compliance.finding.report.description")
    private EntityRef report;
}
