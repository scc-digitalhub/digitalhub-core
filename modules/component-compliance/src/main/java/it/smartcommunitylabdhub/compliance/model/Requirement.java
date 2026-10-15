package it.smartcommunitylabdhub.compliance.model;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A normative compliance requirement derived from a regulatory framework. */
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({
    "id",
    "namespace",
    "name",
    "description",
    "source",
    "requirement_text",
    "summary",
    "kind",
    "compliance_status",
    "related_requirements"
})
public class Requirement extends BaseComplianceObject {

    @Schema(title = "fields.compliance.requirement.source.title", description = "fields.compliance.requirement.source.description")
    private RegulatoryRef source;

    @Schema(title = "fields.compliance.requirement.requirement_text.title", description = "fields.compliance.requirement.requirement_text.description")
    @JsonProperty("requirement_text")
    private String requirementText;

    @Schema(title = "fields.compliance.requirement.kind.title", description = "fields.compliance.requirement.kind.description")
    private RequirementKind kind;

    @Schema(title = "fields.compliance.requirement.related_requirements.title", description = "fields.compliance.requirement.related_requirements.description")
    @JsonProperty("related_requirements")
    private Set<String> relatedRequirements;

    @Schema(title = "fields.compliance.requirement.compliance_status.title", description = "fields.compliance.requirement.compliance_status.description")
    @JsonProperty("compliance_status")
    private ComplianceStatus complianceStatus;

    @Schema(title = "fields.compliance.status.findings.title", description = "fields.compliance.status.findings.description")
    private Set<ComplianceFinding> findings;
    @Schema(title = "fields.compliance.status.mitigations.title", description = "fields.compliance.status.mitigations.description")
    private Set<MitigationAction> mitigations;

    public enum RequirementKind {
        DESIGN,
        TESTING,
        DOCUMENTATION,
        MONITORING,
        DATA_PROTECTION,
        TRANSPARENCY,
        HUMAN_OVERSIGHT,
        DATA_GOVERNANCE,
        RISK_MANAGEMENT,
        CYBERSECURITY,
        QUALITY_MANAGEMENT,
        OPERATIONAL_CONTROL,
        OTHER,
    }

}
