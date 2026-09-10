package it.smartcommunitylabdhub.compliance.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProjectScope {

    @Schema(title = "fields.compliance.project.scope.in_scope.title", description = "fields.compliance.project.scope.in_scope.description")
    /** Capabilities / functions included in the project. */
    @JsonProperty("in_scope")
    private List<String> inScope;
    @Schema(title = "fields.compliance.project.scope.out_of_scope.title", description = "fields.compliance.project.scope.out_of_scope.description")
    /** Explicitly excluded capabilities. */
    @JsonProperty("out_of_scope")
    private List<String> outOfScope;
}
