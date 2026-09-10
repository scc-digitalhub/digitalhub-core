package it.smartcommunitylabdhub.compliance.model;

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
public class EntityRef {

    @Schema(title = "fields.compliance.entity_ref.key.title", description = "fields.compliance.entity_ref.key.description")
    private String key;
}
