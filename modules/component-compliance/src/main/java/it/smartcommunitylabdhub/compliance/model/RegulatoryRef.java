package it.smartcommunitylabdhub.compliance.model;

import java.net.URI;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Normative reference to a regulatory framework article or section. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({
    "framework",
    "article",
    "version",
    "uri"
})
public class RegulatoryRef {

    /** e.g., "EU_AI_ACT", "NIST_AI_RMF", "ISO_42001". */
    @Schema(title = "fields.compliance.regulatory_ref.framework.title", description = "fields.compliance.regulatory_ref.framework.description")
    private String framework;
    /** e.g., "Art. 9", "MAP 1.1". */
    @Schema(title = "fields.compliance.regulatory_ref.article.title", description = "fields.compliance.regulatory_ref.article.description")
    private String article;
    @Schema(title = "fields.compliance.regulatory_ref.version.title", description = "fields.compliance.regulatory_ref.version.description")
    private String version;
    /** Link to official text. */
    @Schema(title = "fields.compliance.regulatory_ref.uri.title", description = "fields.compliance.regulatory_ref.uri.description")
    private URI uri;
}
