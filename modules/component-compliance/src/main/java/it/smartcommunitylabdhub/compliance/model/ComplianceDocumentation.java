package it.smartcommunitylabdhub.compliance.model;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Structured documentation artifact consolidating an entity's specification with its reports.
 * Conforms to Model Card and Data Card standards.
 */
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class ComplianceDocumentation {

    @Schema(title = "fields.compliance.documentation.sections.title", description = "fields.compliance.documentation.sections.description")
    private List<DocumentSection> sections;
}
