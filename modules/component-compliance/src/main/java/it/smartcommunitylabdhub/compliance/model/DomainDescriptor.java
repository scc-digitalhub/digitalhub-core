package it.smartcommunitylabdhub.compliance.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
public class DomainDescriptor {


    /** e.g., "healthcare", "finance", "nlp". */
    @NotNull
    @Schema(title = "fields.compliance.domain.title", description = "fields.compliance.domain.description")
    private String domain;

    @Schema(title = "fields.compliance.subdomain.title", description = "fields.compliance.subdomain.description")
    private String subdomain;
    
    @Schema(title = "fields.compliance.use_case.title", description = "fields.compliance.use_case.description")
    @JsonProperty("use_case")
    private String useCase;
}
