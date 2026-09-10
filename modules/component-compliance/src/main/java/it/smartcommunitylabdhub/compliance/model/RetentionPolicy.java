package it.smartcommunitylabdhub.compliance.model;

import java.time.OffsetDateTime;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Data retention policy defining how long and why data is kept. */
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class RetentionPolicy {

    @Schema(title = "fields.compliance.retention_policy.applies_to.title", description = "fields.compliance.retention_policy.applies_to.description")
    @JsonProperty("applies_to")
    private Set<AppliesToKind> appliesTo;

    @Schema(title = "fields.compliance.retention_policy.retention_basis.title", description = "fields.compliance.retention_policy.retention_basis.description")
    @JsonProperty("retention_basis")
    private RetentionBasis retentionBasis;
    /** Populated when retentionBasis is {@code CUSTOM}. */
    @Schema(title = "fields.compliance.retention_policy.retention_basis_value.title", description = "fields.compliance.retention_policy.retention_basis_value.description")
    @JsonProperty("retention_basis_value")
    private String retentionBasisValue;
    @Schema(title = "fields.compliance.retention_policy.purposes.title", description = "fields.compliance.retention_policy.purposes.description")
    private Set<PurposeKind> purposes;
    
    @Schema(title = "fields.compliance.retention_policy.legal_references.title", description = "fields.compliance.retention_policy.legal_references.description")   
    @JsonProperty("legal_references")
    private Set<RegulatoryRef> legalReferences;
    
    @Schema(title = "fields.compliance.retention_policy.justification.title", description = "fields.compliance.retention_policy.justification.description")
    private String justification;

    @Schema(title = "fields.compliance.retention_policy.owner.title", description = "fields.compliance.retention_policy.owner.description")
    private ActorRef owner;

    @Schema(title = "fields.compliance.retention_policy.approver.title", description = "fields.compliance.retention_policy.approver.description")
    private ActorRef approver;

    @Schema(title = "fields.compliance.retention_policy.valid_from.title", description = "fields.compliance.retention_policy.valid_from.description")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonProperty("valid_from")
    protected OffsetDateTime validFrom;

    @Schema(title = "fields.compliance.retention_policy.valid_until.title", description = "fields.compliance.retention_policy.valid_until.description")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonProperty("valid_until")
    protected OffsetDateTime validUntil;

    public enum AppliesToKind {
        RAW_DATA,
        TRAINING_DATA,
        VALIDATION_DATA,
        TEST_DATA,
        SYNTHETIC_DATA,
        AUGMENTED_DATA,
        TEST_REPORTS,
        DOCUMENTATION,
        OTHER,
    }

    public enum RetentionBasis {
        PURPOSE_NECESSITY,
        LEGAL_OBLIGATION,
        CONSENT_DEPENDENT,
        SECURITY_MONITORING,
        OTHER,
    }

    public enum PurposeKind {
        TRAINING,
        VALIDATION,
        TESTING,
        MONITORING,
        BIAS_DETECTION,
        BIAS_CORRECTION,
        ROBUSTNESS_EVALUATION,
        LEGAL_COMPLIANCE,
        RESEARCH,
        ARCHIVING,
        OTHER,
    }
}
