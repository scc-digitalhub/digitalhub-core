package it.smartcommunitylabdhub.compliance.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReportContent {
 
    @Schema(title = "fields.compliance.report.log.title", description = "fields.compliance.report.log.description")
    private LogContent log;
    @Schema(title = "fields.compliance.report.evaluation.title", description = "fields.compliance.report.evaluation.description")
    private EvaluationContent evaluation;
    @Schema(title = "fields.compliance.report.test.title", description = "fields.compliance.report.test.description")
    private TestContent test;

    //TODO files
}
