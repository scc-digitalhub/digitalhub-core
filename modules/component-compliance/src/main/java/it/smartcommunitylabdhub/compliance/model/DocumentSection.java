package it.smartcommunitylabdhub.compliance.model;

import java.util.List;
import java.util.Map;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Structured section within a Documentation artifact. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DocumentSection {

    @Schema(title = "fields.compliance.documentation.section.title", description = "fields.compliance.documentation.section.description")
    private String title;

    /** Section body in Markdown. */
    @Schema(title = "fields.compliance.documentation.section.content.title", description = "fields.compliance.documentation.section.content.description")
    private String content;

    @Schema(title = "fields.compliance.documentation.section.format.title", description = "fields.compliance.documentation.section.format.description")
    private SectionFormat format;
    /** Populated when format is {@code OTHER}. */
    @Schema(title = "fields.compliance.documentation.section.format_value.title", description = "fields.compliance.documentation.section.format_value.description")
    @JsonProperty("format_value")
    private String formatValue;

    @Schema(title = "fields.compliance.documentation.section.subsections.title", description = "fields.compliance.documentation.section.subsections.description")
    private List<DocumentSection> subsections;

    /** Structured machine-readable representation of the section. */
    @Schema(title = "fields.compliance.documentation.section.specification.title", description = "fields.compliance.documentation.section.specification.description")
    private Map<String, Serializable> specification;
    @Schema(title = "fields.compliance.documentation.section.reports.title", description = "fields.compliance.documentation.section.reports.description")
    private List<EntityRef> reports;

    public enum SectionFormat {
        MODELCARD,
        DATACARD,
        SYSTEMCARD,
        OTHER,
    }
}
