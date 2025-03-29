package it.smartcommunitylabdhub.runtime.kubeai.models;

import io.swagger.v3.oas.annotations.media.Schema;
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
public class KubeAIScaling {


    @Schema(title = "fields.kubeai.replicas.title", description = "fields.kubeai.replicas.description")
    private Integer replicas;

    @Schema(title = "fields.kubeai.minreplicas.title", description = "fields.kubeai.minreplicas.description")
    private Integer minReplicas;

    @Schema(title = "fields.kubeai.maxreplicas.title", description = "fields.kubeai.maxreplicas.description")
    private Integer maxReplicas;

    @Schema(title = "fields.kubeai.autoscalingdisabled.title", description = "fields.kubeai.autoscalingdisabled.description")
    private Boolean autoscalingDisabled;
    
    @Schema(title = "fields.kubeai.targetrequests.title", description = "fields.kubeai.targetrequests.description")
    private Integer targetRequests;

    @Schema(title = "fields.kubeai.scaledowndelayseconds.title", description = "fields.kubeai.scaledowndelayseconds.description")
    private Integer scaleDownDelaySeconds;

    @Schema(title = "fields.kubeai.loadbalancing.title", description = "fields.kubeai.loadbalancing.description")
    private KubeAILoadBalancing loadBalancing;

}
