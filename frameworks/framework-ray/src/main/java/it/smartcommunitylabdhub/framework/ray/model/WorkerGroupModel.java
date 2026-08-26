package it.smartcommunitylabdhub.framework.ray.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
public class WorkerGroupModel {

    private String name;
    private Integer replicas;
    private Integer minReplicas;
    private Integer maxReplicas;
    private PodModel workerSpec;
}
