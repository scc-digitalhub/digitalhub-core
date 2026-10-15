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

/** Profile describing the human oversight configuration for an AI deployment. */
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({
    "oversight_mode",
    "oversight_mode_value",
    "overseer_roles",
    "intervention_points",
    "authority",
    "availability",
    "escalation_channels",
    "limitations",
    "rationale"
})
public class HumanOversightProfile {

    @Schema(title = "fields.compliance.human_oversight_profile.oversight_mode.title", description = "fields.compliance.human_oversight_profile.oversight_mode.description")
    @JsonProperty("oversight_mode")
    private OversightMode oversightMode;
    /** Populated when oversightMode is {@code OTHER}. */
    @Schema(title = "fields.compliance.human_oversight_profile.oversight_mode_value.title", description = "fields.compliance.human_oversight_profile.oversight_mode_value.description")
    @JsonProperty("oversight_mode_value")
    private String oversightModeValue;

    @Schema(title = "fields.compliance.human_oversight_profile.overseer_roles.title", description = "fields.compliance.human_oversight_profile.overseer_roles.description")
    @JsonProperty("overseer_roles")
    private Set<ActorRole> overseerRoles;
    @Schema(title = "fields.compliance.human_oversight_profile.overseer_groups.title", description = "fields.compliance.human_oversight_profile.overseer_groups.description")
    @JsonProperty("intervention_points")
    private Set<InterventionPoint> interventionPoints;
    
    @Schema(title = "fields.compliance.human_oversight_profile.authority.title", description = "fields.compliance.human_oversight_profile.authority.description")
    private Set<Authority> authority;
    
    @Schema(title = "fields.compliance.human_oversight_profile.availability.title", description = "fields.compliance.human_oversight_profile.availability.description")
    private Availability availability;
    
    @Schema(title = "fields.compliance.human_oversight_profile.escalation_channels.title", description = "fields.compliance.human_oversight_profile.escalation_channels.description")
    @JsonProperty("escalation_channels")
    private Set<EscalationChannel> escalationChannels;
    
    //TODO: clarify usage
    @Schema(title = "fields.compliance.human_oversight_profile.limitations.title", description = "fields.compliance.human_oversight_profile.limitations.description")
    private Set<String> limitations;
    
    private String rationale;

    public enum OversightMode {
        HUMAN_IN_THE_LOOP,
        HUMAN_IN_COMMAND,
        PRE_DEPLOYMENT_APPROVAL,
        POST_HOC_REVIEW,
        EXCEPTION_BASED_REVIEW,
        FULLY_AUTOMATED_WITH_ESCALATION,
        MANUAL_OPERATION,
        NONE,
        OTHER,
    }

    public enum InterventionPoint {
        DATA_APPROVAL,
        TRAINING_APPROVAL,
        TEST_APPROVAL,
        THRESHOLD_APPROVAL,
        DEPLOYMENT_APPROVAL,
        INPUT_REVIEW,
        OUTPUT_REVIEW,
        DECISION_APPROVAL,
        ABSTENTION_REVIEW,
        ESCALATION_REVIEW,
        INCIDENT_REVIEW,
        OTHER,
    }

    public enum Authority {
        VIEW_ONLY,
        REQUEST_EXPLANATION,
        APPROVE,
        REJECT,
        OVERRIDE_OUTPUT,
        ESCALATE,
        SUSPEND_SERVICE,
        RETIRE_MODEL,
        OTHER,
    }

    public enum Availability {
        CONTINUOUS,
        BUSINESS_HOURS,
        ON_CALL,
        BATCH_REVIEW,
        PERIODIC,
        NONE,
    }

    public enum EscalationChannel {
        HUMAN_REVIEW,
        COMPLIANCE_REVIEW,
        TECHNICAL_REVIEW,
        LEGAL_REVIEW,
        MANAGEMENT_APPROVAL,
        OTHER,
    }
}
