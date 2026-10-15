package it.smartcommunitylabdhub.compliance.model;
import java.time.Duration;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Risk classification of an AI system within a compliance context. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({
    "risk_level",
    "risk_level_value",
    "classification_status",
    "rationale",
    "assessor",
    "approver",
    "assessed_at",
    "valid_until",
    "review_cadence"
})
public class RiskClassification {

    @Schema(title = "fields.compliance.risk_classification.risk_level.title", description = "fields.compliance.risk_classification.risk_level.description")
    @JsonProperty("risk_level")
    private RiskLevel riskLevel;
    /** Populated when riskLevel is {@code OTHER}. */
    @Schema(title = "fields.compliance.risk_classification.risk_level_value.title", description = "fields.compliance.risk_classification.risk_level_value.description")
    @JsonProperty("risk_level_value")
    private String riskLevelValue;
    
    @Schema(title = "fields.compliance.risk_classification.classification_status.title", description = "fields.compliance.risk_classification.classification_status.description")
    @JsonProperty("classification_status")
    private ClassificationStatus classificationStatus;
    
    @Schema(title = "fields.compliance.risk_classification.rationale.title", description = "fields.compliance.risk_classification.rationale.description")
    private String rationale;
    
    @Schema(title = "fields.compliance.risk_classification.assessor.title", description = "fields.compliance.risk_classification.assessor.description")
    private ActorRef assessor;
    @Schema(title = "fields.compliance.risk_classification.approver.title", description = "fields.compliance.risk_classification.approver.description")
    private ActorRef approver;
    
    @Schema(title = "fields.compliance.risk_classification.assessed_at.title", description = "fields.compliance.risk_classification.assessed_at.description")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonProperty("assessed_at")
    protected OffsetDateTime assessedAt;

    @Schema(title = "fields.compliance.risk_classification.valid_until.title", description = "fields.compliance.risk_classification.valid_until.description")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonProperty("valid_until")
    protected OffsetDateTime validUntil;

    @Schema(title = "fields.compliance.risk_classification.review_cadence.title", description = "fields.compliance.risk_classification.review_cadence.description")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonProperty("review_cadence")
    private Duration reviewCadence;

    public enum RiskLevel {
        PROHIBITED,
        HIGH,
        LIMITED,
        MINIMAL,
        UNKNOWN,
        OTHER,
    }

    public enum ClassificationStatus {
        PRELIMINARY,
        ASSESSED,
        APPROVED,
        NEEDS_REVIEW,
        SUPERSEDED,
    }
}
