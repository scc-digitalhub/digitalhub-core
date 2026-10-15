package it.smartcommunitylabdhub.compliance.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Governance metadata for a dataset. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({
    "sensitivity",
    "license",
    "retention",
    "consent"
})
public class DataGovernance {

    @Schema(title = "fields.compliance.data_governance.sensitivity.title", description = "fields.compliance.data_governance.sensitivity.description")
    private SensitivityLevel sensitivity;
    /** SPDX license expression. */
    @Schema(title = "fields.compliance.data_governance.license.title", description = "fields.compliance.data_governance.license.description")
    private String license;

    @Schema(title = "fields.compliance.data_governance.retention.title", description = "fields.compliance.data_governance.retention.description")
    private RetentionPolicy retention;
    
    @Schema(title = "fields.compliance.data_governance.consent.title", description = "fields.compliance.data_governance.consent.description")
    private ConsentSpec consent;
}
