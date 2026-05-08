package it.smartcommunitylabdhub.framework.ray.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import it.smartcommunitylabdhub.framework.k8s.objects.CorePort;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreServiceType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RayServiceModel extends RayModel {

    private List<String> serviceNames;
    private List<CorePort> servicePorts;
    private CoreServiceType serviceType;

    private Map<String, Serializable> serviceConf;

    private Integer rayClusterDeletionDelaySeconds;
}
