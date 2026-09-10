package it.smartcommunitylabdhub.compliance.specs;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.models.base.BaseSpec;
import it.smartcommunitylabdhub.compliance.model.ComplianceDocumentation;
import it.smartcommunitylabdhub.compliance.model.ComplianceStatus;
import it.smartcommunitylabdhub.compliance.model.ComplianceObjective;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class BaseComplianceSpec extends BaseSpec {
    
    @Builder.Default
    @Schema(title = "fields.compliance.compliance.objectives.title", description = "fields.compliance.compliance.objectives.description")
    private Set<ComplianceObjective> objectives = new HashSet<>();

    @Schema(title = "fields.compliance.compliance.compliance_status.title", description = "fields.compliance.compliance.compliance_status.description")
    @JsonProperty("compliance_status")
    private ComplianceStatus complianceStatus;

    @Schema(title = "fields.compliance.compliance.documentation.title", description = "fields.compliance.compliance.documentation.description")
    private ComplianceDocumentation documentation;


    @Override
    public void configure(Map<String, Serializable> data) {
        BaseComplianceSpec spec = mapper.convertValue(data, BaseComplianceSpec.class);
        this.objectives = spec.getObjectives();
        this.complianceStatus = spec.getComplianceStatus();
        this.documentation = spec.getDocumentation();
    }
}
