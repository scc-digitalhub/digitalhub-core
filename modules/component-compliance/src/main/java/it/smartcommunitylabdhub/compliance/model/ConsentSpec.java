package it.smartcommunitylabdhub.compliance.model;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Specification of data subject consent requirements and conditions. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConsentSpec {

    @Schema(title = "fields.compliance.consent_spec.consent_required.title", description = "fields.compliance.consent_spec.consent_required.description")
    @JsonProperty("consent_required")
    private Boolean consentRequired;
    @JsonProperty("consent_basis")
    @Schema(title = "fields.compliance.consent_spec.consent_basis.title", description = "fields.compliance.consent_spec.consent_basis.description")
    private ConsentBasis consentBasis;
    /** Populated when consentBasis is {@code OTHER}. */
    @Schema(title = "fields.compliance.consent_spec.consent_basis_value.title", description = "fields.compliance.consent_spec.consent_basis_value.description")
    @JsonProperty("consent_basis_value")
    private String consentBasisValue;
    @Schema(title = "fields.compliance.consent_spec.applies_to.title", description = "fields.compliance.consent_spec.applies_to.description")
    @JsonProperty("applies_to")
    private Set<AppliesToKind> appliesTo;
    @Schema(title = "fields.compliance.consent_spec.data_categories.title", description = "fields.compliance.consent_spec.data_categories.description")
    @JsonProperty("data_categories")
    private Set<DataCategory> dataCategories;
    @Schema(title = "fields.compliance.consent_spec.purposes.title", description = "fields.compliance.consent_spec.purposes.description")
    private Set<PurposeKind> purposes;
    
    @JsonProperty("freely_given")
    @Schema(title = "fields.compliance.consent_spec.freely_given.title", description = "fields.compliance.consent_spec.freely_given.description")
    private Boolean freelyGiven;
    @Schema(title = "fields.compliance.consent_spec.specific.title", description = "fields.compliance.consent_spec.specific.description")
    private Boolean specific;
    @Schema(title = "fields.compliance.consent_spec.informed.title", description = "fields.compliance.consent_spec.informed.description")
    private Boolean informed;
    @Schema(title = "fields.compliance.consent_spec.unambiguous.title", description = "fields.compliance.consent_spec.unambiguous.description")
    private Boolean unambiguous;
    @Schema(title = "fields.compliance.consent_spec.explicit.title", description = "fields.compliance.consent_spec.explicit.description")
    private Boolean explicit;

    public enum ConsentBasis {
        GDPR_ARTICLE_6_1_A,
        GDPR_ARTICLE_9_2_A,
        AI_ACT_REAL_WORLD_TESTING,
        OTHER,
    }

    public enum AppliesToKind {
        COLLECTION,
        STORAGE,
        TRAINING,
        VALIDATION,
        TESTING,
        PROFILING,
        AUTOMATED_DECISION_SUPPORT,
        BIAS_DETECTION,
        BIAS_CORRECTION,
        ROBUSTNESS_EVALUATION,
        DATA_SHARING,
        OTHER,
    }

    public enum DataCategory {
        PERSONAL,
        SENSITIVE,
        ANONYMIZED,
        PSEUDONYMIZED,
        SYNTHETIC,
        PUBLICLY_AVAILABLE,
        PROPRIETARY,
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
