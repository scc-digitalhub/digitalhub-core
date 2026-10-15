package it.smartcommunitylabdhub.compliance.model;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.experimental.SuperBuilder;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
/** Specification of a population group relevant to a compliance context. */
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
    "rationale",
    "group_kind",
    "group_kind_value",
    "group_role",
    "group_role_value",
    "defining_attributes",
    "vulnerability",
    "vulnerability_value",
})
public class GroupSpec extends BaseComplianceObject {

    @Schema(title = "fields.compliance.group_spec.group_kind.title", description = "fields.compliance.group_spec.group_kind.description")
    @JsonProperty("group_kind")
    private GroupKind groupKind;
    /** Populated when groupKind is {@code OTHER}. */
    @Schema(title = "fields.compliance.group_spec.group_kind_value.title", description = "fields.compliance.group_spec.group_kind_value.description")
    @JsonProperty("group_kind_value")
    private String groupKindValue;

    @Schema(title = "fields.compliance.group_spec.group_role.title", description = "fields.compliance.group_spec.group_role.description")
    @JsonProperty("group_role")
    private GroupRole groupRole;
    /** Populated when groupRole is {@code OTHER}. */
    @Schema(title = "fields.compliance.group_spec.group_role_value.title", description = "fields.compliance.group_spec.group_role_value.description")
    @JsonProperty("group_role_value")
    private String groupRoleValue;

    @Schema(title = "fields.compliance.group_spec.defining_attributes.title", description = "fields.compliance.group_spec.defining_attributes.description")
    @JsonProperty("defining_attributes")
    private Set<AttributeRef> definingAttributes;

    @Schema(title = "fields.compliance.group_spec.vulnerability.title", description = "fields.compliance.group_spec.vulnerability.description")
    @JsonProperty("vulnerability")
    private VulnerabilityKind vulnerability;
    /** Populated when vulnerability is {@code OTHER}. */
    @Schema(title = "fields.compliance.group_spec.vulnerability_value.title", description = "fields.compliance.group_spec.vulnerability_value.description")
    @JsonProperty("vulnerability_value")
    private String vulnerabilityValue;

    @Schema(title = "fields.compliance.group_spec.rationale.title", description = "fields.compliance.group_spec.rationale.description")
    private String rationale;

    public enum GroupKind {
        DEMOGRAPHIC,
        PROTECTED,
        VULNERABLE,
        OCCUPATIONAL,
        USER_GROUP,
        GEOGRAPHIC,
        LANGUAGE_GROUP,
        SOCIOECONOMIC,
        TECHNICAL_ENVIRONMENT,
        OTHER,
    }

    public enum GroupRole {
        TARGET_SUBJECT,
        DECISION_SUBJECT,
        END_USER,
        OPERATOR,
        BENEFICIARY,
        EXCLUDED_POPULATION,
        REFERENCE_GROUP,
        OTHER,
    }

    public enum VulnerabilityKind {
        AGE,
        DISABILITY,
        HEALTH_STATUS,
        ECONOMIC_DEPENDENCY,
        EDUCATIONAL_ACCESS,
        DIGITAL_LITERACY,
        EMPLOYMENT_DEPENDENCY,
        MINORITY_STATUS,
        LANGUAGE_ACCESS,
        OTHER,
    }
}
