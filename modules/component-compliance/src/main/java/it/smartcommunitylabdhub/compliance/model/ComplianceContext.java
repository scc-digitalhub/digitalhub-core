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

/**
 * Contextualizes an AI project's usage with respect to intended purpose and applicable compliance
 * requirements.
 */
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({
    "environment",
    "geography",
    "regulations",
    "requirements",
    "risk_classification",
    "actor_role",
    "actor_role_value",
    "affected_group",
    "vulnerable_group",
    "protected_attributes",
    "foreseeable_misuse",
    "human_oversight",
})
public class ComplianceContext {

    @Schema(title = "fields.compliance.context.environment.title", description = "fields.compliance.context.environment.description")
    private EnvironmentKind environment;
    @Schema(title = "fields.compliance.context.regulations.title", description = "fields.compliance.context.regulations.description")
    private Set<RegulatoryRef> regulations;
    
    /** Jurisdictions of deployment. */
    @Schema(title = "fields.compliance.context.geography.title", description = "fields.compliance.context.geography.description")
    private Set<String> geography;
    
    // TODO: clarify usage
    @Schema(title = "fields.compliance.context.actor_role.title", description = "fields.compliance.context.actor_role.description")
    @JsonProperty("actor_role")
    private ActorRole actorRole;
    /** Populated when actorRole is {@code OTHER}. */
    @Schema(title = "fields.compliance.context.actor_role_value.title", description = "fields.compliance.context.actor_role_value.description")
    @JsonProperty("actor_role_value")
    private String actorRoleValue;

    @Schema(title = "fields.compliance.context.affected_group.title", description = "fields.compliance.context.affected_group.description")
    @JsonProperty("affected_group")
    private Set<GroupSpec> affectedGroups;

    @Schema(title = "fields.compliance.context.vulnerable_group.title", description = "fields.compliance.context.vulnerable_group.description")
    @JsonProperty("vulnerable_group")
    private Set<GroupSpec> vulnerableGroups;

    // TODO: clarify usage
    @Schema(title = "fields.compliance.context.protected_attributes.title", description = "fields.compliance.context.protected_attributes.description")
    @JsonProperty("protected_attributes")
    private Set<AttributeRef> protectedAttributes;

    @Schema(title = "fields.compliance.context.foreseeable_misuse.title", description = "fields.compliance.context.foreseeable_misuse.description")
    @JsonProperty("foreseeable_misuse")
    private Set<MisuseScenario> foreseeableMisuse;

    @Schema(title = "fields.compliance.context.human_oversight.title", description = "fields.compliance.context.human_oversight.description")
    @JsonProperty("human_oversight")
    private HumanOversightProfile humanOversight;

    @Schema(title = "fields.compliance.context.risk_classification.title", description = "fields.compliance.context.risk_classification.description")
    @JsonProperty("risk_classification")
    private RiskClassification riskClassification;

    @Schema(title = "fields.compliance.context.requirements.title", description = "fields.compliance.context.requirements.description")
    private Set<Requirement> requirements;

    public enum EnvironmentKind {
        PRODUCTION,
        SANDBOX,
        PILOT,
    }
}
