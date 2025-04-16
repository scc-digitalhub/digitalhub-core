package it.smartcommunitylabdhub.runtime.kubeai.models;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @Builder.Default
    private Integer replicas = 1;

    @Schema(title = "fields.kubeai.minreplicas.title", description = "fields.kubeai.minreplicas.description")
    @Builder.Default
    @JsonProperty("min_replicas")
    private Integer minReplicas = 1;

    @Schema(title = "fields.kubeai.maxreplicas.title", description = "fields.kubeai.maxreplicas.description")
    @JsonProperty("max_replicas")
    private Integer maxReplicas;

    @Schema(
        title = "fields.kubeai.autoscalingdisabled.title",
        description = "fields.kubeai.autoscalingdisabled.description"
    )
    @Builder.Default
    @JsonProperty("autoscaling_disabled")
    private Boolean autoscalingDisabled = false;

    @Schema(title = "fields.kubeai.targetrequests.title", description = "fields.kubeai.targetrequests.description")
    @Builder.Default
    @JsonProperty("target_requests")
    private Integer targetRequests = 100;

    @Schema(
        title = "fields.kubeai.scaledowndelayseconds.title",
        description = "fields.kubeai.scaledowndelayseconds.description"
    )
    @Builder.Default
    @JsonProperty("scale_down_delay_seconds")
    private Integer scaleDownDelaySeconds = 30;

    @Schema(title = "fields.kubeai.loadbalancing.title", description = "fields.kubeai.loadbalancing.description")
    @JsonProperty("load_balancing")
    private KubeAILoadBalancing loadBalancing;
}
