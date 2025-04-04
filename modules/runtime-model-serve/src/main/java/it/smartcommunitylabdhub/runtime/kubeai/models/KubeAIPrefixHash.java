package it.smartcommunitylabdhub.runtime.kubeai.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KubeAIPrefixHash {

    @Schema(title = "fields.kubeai.meanloadfactor.title", description = "fields.kubeai.meanloadfactor.description")
    @Builder.Default
    private Integer meanLoadFactor = 125;
    @Schema(title = "fields.kubeai.replication.title", description = "fields.kubeai.replication.description" )
    @Builder.Default
    private Integer replication = 256;
    @Schema(title = "fields.kubeai.prefixcharlength.title", description = "fields.kubeai.prefixcharlength.description")
    @Builder.Default
    private Integer prefixCharLength = 100;
}
