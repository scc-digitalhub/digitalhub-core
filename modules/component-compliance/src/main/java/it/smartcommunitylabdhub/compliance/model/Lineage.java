package it.smartcommunitylabdhub.compliance.model;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** DAG of derivation relationships tracking how an artifact was produced. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Lineage {

    /** Upstream artifacts consumed. */
    @Schema(title = "fields.compliance.lineage.sources.title", description = "fields.compliance.lineage.sources.description")
    private List<String> sources;
    /** Operation run that produced this artifact. */
    @Schema(title = "fields.compliance.lineage.operation.title", description = "fields.compliance.lineage.operation.description")
    private String operation;
}
