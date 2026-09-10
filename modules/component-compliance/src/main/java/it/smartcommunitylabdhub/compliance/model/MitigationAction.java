package it.smartcommunitylabdhub.compliance.model;

import java.time.OffsetDateTime;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Operation engaged to resolve a compliance objective violation, producing a corrected entity for
 * which the objective is satisfied.
 */
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({"rationale", "status", "residual_status", "evidence", "owner", "due_at", "applied_at", "verification_report"})
public class MitigationAction {

    @Schema(title = "fields.compliance.mitigation.status.title", description = "fields.compliance.mitigation.status.description")
    private MitigationStatus status;
    @Schema(title = "fields.compliance.mitigation.rationale.title", description = "fields.compliance.mitigation.rationale.description")
    private String rationale;
    @Schema(title = "fields.compliance.mitigation.owner.title", description = "fields.compliance.mitigation.owner.description")
    private ActorRef owner;

    @Schema(title = "fields.compliance.mitigation.due_at.title", description = "fields.compliance.mitigation.due_at.description")
    @JsonProperty("due_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    protected OffsetDateTime dueAt;

    @Schema(title = "fields.compliance.mitigation.applied_at.title", description = "fields.compliance.mitigation.applied_at.description")
    @JsonProperty("applied_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    protected OffsetDateTime appliedAt;

    @Schema(title = "fields.compliance.mitigation.evidence.title", description = "fields.compliance.mitigation.evidence.description")
    private Set<EntityRef> evidence;

    @Schema(title = "fields.compliance.mitigation.verification_report.title", description = "fields.compliance.mitigation.verification_report.description")
    @JsonProperty("verification_report")
    private EntityRef verificationReport;

    @Schema(title = "fields.compliance.mitigation.residual_status.title", description = "fields.compliance.mitigation.residual_status.description")
    @JsonProperty("residual_status")
    private ResidualStatus residualStatus;

    public enum MitigationStatus {
        NOT_REQUIRED,
        PROPOSED,
        PLANNED,
        IN_PROGRESS,
        APPLIED,
        VERIFIED,
        REJECTED,
        DEFERRED,
        ACCEPTED_RISK,
    }

    public enum ResidualStatus {
        OPEN,
        PARTIALLY_RESOLVED,
        RESOLVED,
        UNRESOLVED,
        ACCEPTED_RESIDUAL_RISK,
        NEEDS_RETEST,
    }
}
