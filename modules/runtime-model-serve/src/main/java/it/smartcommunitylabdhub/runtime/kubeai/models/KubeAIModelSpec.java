package it.smartcommunitylabdhub.runtime.kubeai.models;

import java.util.List;
import java.util.Map;

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
public class KubeAIModelSpec {

    private String url;
    private List<KubeAIAdapter> adapters;
    private String engine;
    private List<String> features;
    private String image;

    private List<String> args;
    private String resourceProfile;
    private String cacheProfile;
    private Map<String, String> env;
    private Integer replicas;
    private Integer minReplicas;
    private Integer maxReplicas;
    private Boolean autoscalingDisabled;
    private Integer targetRequests;
    private Integer scaleDownDelaySeconds;
    private KubeAILoadBalancing loadBalancing;
    private List<KubeAIFile> files;
}
