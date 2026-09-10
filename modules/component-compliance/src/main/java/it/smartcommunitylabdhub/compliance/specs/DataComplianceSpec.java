package it.smartcommunitylabdhub.compliance.specs;
import it.smartcommunitylabdhub.artifacts.Artifact;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.compliance.model.DataGovernance;
import it.smartcommunitylabdhub.dataitems.DataItem;
import it.smartcommunitylabdhub.compliance.model.AttributeRef;
import it.smartcommunitylabdhub.extensions.annotations.ExtensionType;
import it.smartcommunitylabdhub.extensions.model.Extension;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@SpecType(kind = "data-compliance", entity = Extension.class)
@ExtensionType(appliesTo = { DataItem.class, Artifact.class }, showIn = "tabs")
@JsonPropertyOrder({
    "scope",
    "governance",
    "attributes"
})
public class DataComplianceSpec extends BaseComplianceSpec {

    private DataScope scope;
    private DataGovernance governance;
    private Set<AttributeRef> attributes;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);
        DataComplianceSpec spec = mapper.convertValue(data, DataComplianceSpec.class);
        this.governance = spec.getGovernance();
        this.attributes = spec.getAttributes();
    }

    public static enum DataScope {
        TRAINING,
        VALIDATION,
        SUPPORT
    }
}
