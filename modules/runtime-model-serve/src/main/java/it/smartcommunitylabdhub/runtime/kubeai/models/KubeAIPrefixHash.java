package it.smartcommunitylabdhub.runtime.kubeai.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KubeAIPrefixHash {

    @Schema(title = "fields.kubeai.meanloadfactor.title", description = "fields.kubeai.meanloadfactor.description")
    private Integer meanLoadFactor;
    @Schema(title = "fields.kubeai.replication.title", description = "fields.kubeai.replication.description")
    private Integer replication;
    @Schema(title = "fields.kubeai.prefixcharlength.title", description = "fields.kubeai.prefixcharlength.description")
    private Integer prefixCharLength;
}
