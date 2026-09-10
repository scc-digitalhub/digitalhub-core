package it.smartcommunitylabdhub.compliance.model;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Assessed compliance status of a project or entity at a point in time. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({"status",  "assessor", "assessed_at", "findings", "mitigations"})
public class ComplianceStatus {

    
    @Schema(title = "fields.compliance.status.status.title", description = "fields.compliance.status.status.description")
    private StatusKind status;

    @Schema(title = "fields.compliance.status.assessed_at.title", description = "fields.compliance.status.assessed_at.description")
    @JsonProperty("assessed_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    protected OffsetDateTime assessedAt;
    
    @Schema(title = "fields.compliance.status.assessor.title", description = "fields.compliance.status.assessor.description")
    private ActorRef assessor;

    @Schema(title = "fields.compliance.status.findings.title", description = "fields.compliance.status.findings.description")
    private Set<ComplianceFinding> findings;
    @Schema(title = "fields.compliance.status.mitigations.title", description = "fields.compliance.status.mitigations.description")
    private Set<MitigationAction> mitigations;

    public enum StatusKind {
        COMPLIANT,
        NON_COMPLIANT,
        PARTIALLY_COMPLIANT,
        PENDING_REVIEW,
    }
}
