package it.smartcommunitylabdhub.framework.ray.model.ray;

import java.util.Map;

import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerGroupSpec {
    // TODO: scaleStrategy, numOfHosts, idleTimeoutSeconds

    private String groupName;
    private Integer replicas;
    private Integer minReplicas;
    private Integer maxReplicas;

    private V1PodTemplateSpec template;

    private Map<String, String> resources;
    private Map<String, String> labels;
    private Map<String, String> rayStartParams;

}
