package it.smartcommunitylabdhub.compliance.model;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
/** Description of a foreseeable misuse scenario for an AI system. */
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({
    "misuse_kind",
    "misuse_kind_value",
    "intentionality",
    "likelihood",
    "impact",
    "actor_roles",
    "affected_groups",
    "rationale"
})
public class MisuseScenario extends BaseComplianceObject {

    @Schema(title = "fields.compliance.misuse_scenario.misuse_kind.title", description = "fields.compliance.misuse_scenario.misuse_kind.description")
    private MisuseKind misuseKind;
    /** Populated when misuseKind is {@code OTHER}. */
    @Schema(title = "fields.compliance.misuse_scenario.misuse_kind_value.title", description = "fields.compliance.misuse_scenario.misuse_kind_value.description")
    private String misuseKindValue;

    @Schema(title = "fields.compliance.misuse_scenario.intentionality.title", description = "fields.compliance.misuse_scenario.intentionality.description")
    private Intentionality intentionality;
    @Schema(title = "fields.compliance.misuse_scenario.likelihood.title", description = "fields.compliance.misuse_scenario.likelihood.description")
    private Likelihood likelihood;
    @Schema(title = "fields.compliance.misuse_scenario.impact.title", description = "fields.compliance.misuse_scenario.impact.description")
    private Impact impact;

    @Schema(title = "fields.compliance.misuse_scenario.actor_roles.title", description = "fields.compliance.misuse_scenario.actor_roles.description")
    @JsonProperty("actor_roles")
    private Set<ActorRole> actorRoles;

    @Schema(title = "fields.compliance.misuse_scenario.affected_groups.title", description = "fields.compliance.misuse_scenario.affected_groups.description")
    @JsonProperty("affected_groups")
    private Set<GroupSpec> affectedGroups;

    @Schema(title = "fields.compliance.misuse_scenario.rationale.title", description = "fields.compliance.misuse_scenario.rationale.description")
    private String rationale;

    public enum MisuseKind {
        OUT_OF_SCOPE_USE,
        OVERRELIANCE,
        AUTOMATION_BIAS,
        HUMAN_OVERSIGHT_BYPASS,
        DECISION_AUTOMATION_BEYOND_PURPOSE,
        UNAUTHORIZED_ACCESS,
        DATA_LEAKAGE,
        DATA_POISONING,
        MODEL_POISONING,
        ADVERSARIAL_INPUT,
        PROMPT_INJECTION,
        MODEL_EXTRACTION,
        MONITORING_EVASION,
        FEEDBACK_LOOP_AMPLIFICATION,
        UNSAFE_FALLBACK_USE,
        MISINTERPRETATION_OF_OUTPUT,
        DEPLOYMENT_IN_UNVALIDATED_CONTEXT,
        OTHER,
    }

    public enum Intentionality {
        ACCIDENTAL,
        NEGLIGENT,
        INTENTIONAL,
        ADVERSARIAL,
        UNKNOWN,
    }

    public enum Likelihood {
        RARE,
        UNLIKELY,
        POSSIBLE,
        LIKELY,
        ALMOST_CERTAIN,
        UNKNOWN,
    }

    public enum Impact {
        NEGLIGIBLE,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL,
        UNKNOWN,
    }
}
