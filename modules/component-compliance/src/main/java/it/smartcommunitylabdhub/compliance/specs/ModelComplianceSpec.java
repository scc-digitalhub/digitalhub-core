package it.smartcommunitylabdhub.compliance.specs;
import org.springframework.ui.Model;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.extensions.annotations.ExtensionType;
import it.smartcommunitylabdhub.extensions.model.Extension;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
@SpecType(kind = "model-compliance", entity = Extension.class)
@ExtensionType(appliesTo = { Model.class }, showIn = "tabs")
public class ModelComplianceSpec extends BaseComplianceSpec {}
