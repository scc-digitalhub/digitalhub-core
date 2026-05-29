package it.smartcommunitylabdhub.framework.ray.model;

import java.util.List;

import it.smartcommunitylabdhub.framework.k8s.objects.CorePort;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreServiceType;
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
public class ClusterModel {

    private String version;

    private CoreServiceType headServiceType;

    private PodModel headSpec;
    private List<WorkerGroupModel> workerGroups;
}
