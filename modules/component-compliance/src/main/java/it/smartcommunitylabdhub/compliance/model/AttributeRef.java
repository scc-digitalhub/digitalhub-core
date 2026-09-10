package it.smartcommunitylabdhub.compliance.model;

import java.util.Set;
import lombok.experimental.SuperBuilder;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Descriptor of a data attribute with semantic and observability metadata. */
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
    "semantic_type",
    "semantic_type_value",
    "attribute_role",
    "attribute_role_value",
    "proxy_for",
    "observability",
    "use_permissions"
})
public class AttributeRef  extends BaseComplianceObject {

    @Schema(title = "fields.compliance.attribute_ref.semantic_type.title", description = "fields.compliance.attribute_ref.semantic_type.description")
    @JsonProperty("semantic_type")
    private SemanticType semanticType;
    /** Populated when semanticType is {@code CUSTOM}. */
    @Schema(title = "fields.compliance.attribute_ref.semantic_type_value.title", description = "fields.compliance.attribute_ref.semantic_type_value.description")
    @JsonProperty("semantic_type_value")
    private String semanticTypeValue;

    @Schema(title = "fields.compliance.attribute_ref.attribute_role.title", description = "fields.compliance.attribute_ref.attribute_role.description")
    @JsonProperty("attribute_role")
    private AttributeRole attributeRole;
    /** Populated when attributeRole is {@code OTHER}. */
    @Schema(title = "fields.compliance.attribute_ref.attribute_role_value.title", description = "fields.compliance.attribute_ref.attribute_role_value.description")
    @JsonProperty("attribute_role_value")
    private String attributeRoleValue;

    @Schema(title = "fields.compliance.attribute_ref.proxy_for.title", description = "fields.compliance.attribute_ref.proxy_for.description")
    @JsonProperty("proxy_for")
    private Set<AttributeRef> proxyFor;

    @Schema(title = "fields.compliance.attribute_ref.observability.title", description = "fields.compliance.attribute_ref.observability.description")
    private Observability observability;

    @Schema(title = "fields.compliance.attribute_ref.use_permissions.title", description = "fields.compliance.attribute_ref.use_permissions.description")
    @JsonProperty("use_permissions")
    private Set<UsePermission> usePermissions;

    public enum SemanticType {
        AGE,
        SEX,
        GENDER,
        RACE_ETHNICITY,
        NATIONALITY,
        LANGUAGE,
        RELIGION,
        DISABILITY,
        HEALTH_STATUS,
        LOCATION,
        SOCIOECONOMIC_STATUS,
        EDUCATION,
        EMPLOYMENT_STATUS,
        OTHER,
    }

    public enum AttributeRole {
        INPUT_FEATURE,
        TARGET,
        PROTECTED_ATTRIBUTE,
        SENSITIVE_ATTRIBUTE,
        PROXY_ATTRIBUTE,
        GROUP_ATTRIBUTE,
        STRATIFICATION_ATTRIBUTE,
        TIMESTAMP,
        OTHER,
    }

    public enum Observability {
        DIRECT,
        SELF_REPORTED,
        INFERRED,
        DERIVED,
        PROXY,
        EXTERNAL,
        UNOBSERVED,
        UNKNOWN,
    }

    public enum UsePermission {
        MODEL_INPUT,
        TRAINING,
        EVALUATION,
        FAIRNESS_TESTING,
        ROBUSTNESS_SLICING,
        MONITORING,
        MITIGATION,
        REPORTING,
        EXCLUDED_FROM_MODEL,
        RESTRICTED,
        UNKNOWN,
    }
}
