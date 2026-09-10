package it.smartcommunitylabdhub.compliance.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class BaseComplianceObject  {

    @Schema(title = "fields.compliance.id.title", description = "fields.compliance.id.description")
    private String id;
    @Schema(title = "fields.compliance.namespace.title", description = "fields.compliance.namespace.description")
    private String namespace;

    @Schema(title = "fields.compliance.name.title", description = "fields.compliance.name.description")
    private String name;
    @Schema(title = "fields.compliance.description.title", description = "fields.compliance.description.description")
    private String description;
}
