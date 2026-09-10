package it.smartcommunitylabdhub.compliance.specs;
import it.smartcommunitylabdhub.artifacts.Artifact;
import it.smartcommunitylabdhub.dataitems.DataItem;
import it.smartcommunitylabdhub.models.Model;
import java.util.Map;

import it.smartcommunitylabdhub.compliance.model.Lineage;
import it.smartcommunitylabdhub.compliance.model.ReportContent;
import java.io.Serializable;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.HashSet;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.base.BaseSpec;
import it.smartcommunitylabdhub.extensions.annotations.ExtensionType;
import it.smartcommunitylabdhub.extensions.model.Extension;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SpecType(kind = "compliance-report", entity = Extension.class)
@ExtensionType(appliesTo = { DataItem.class, Artifact.class, Model.class })
public class ReportSpec extends BaseSpec {

    @Schema(title = "fields.compliance.report.kind.title", description = "fields.compliance.report.kind.description")
    private ReportKind kind;
    /** Content discriminated by kind: EvaluationContent, TestContent, or LogContent. */
    @Schema(title = "fields.compliance.report.content.title", description = "fields.compliance.report.content.description")
    private ReportContent content;

    @Schema(title = "fields.compliance.report.lineage.title", description = "fields.compliance.report.lineage.description")
    private Lineage lineage;

    @Schema(title = "fields.compliance.report.objectives.title", description = "fields.compliance.report.objectives.description")
    private Set<String> objectives = new HashSet<>();
    
    public enum ReportKind {
        EVALUATION,
        TEST,
        LOG,
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        ReportSpec spec = mapper.convertValue(data, ReportSpec.class);
        this.kind = spec.getKind();
        this.content = spec.getContent();
        this.objectives = spec.getObjectives();
        this.lineage = spec.getLineage();
    }
}
