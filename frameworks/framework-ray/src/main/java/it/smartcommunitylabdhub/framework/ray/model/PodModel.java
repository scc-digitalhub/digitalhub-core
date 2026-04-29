package it.smartcommunitylabdhub.framework.ray.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import it.smartcommunitylabdhub.framework.k8s.objects.CoreAffinity;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreImagePullPolicy;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreNodeSelector;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreResources;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreToleration;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
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
public class PodModel {

    private String image;
    private CoreResources resources;
    private List<CoreVolume> volumes;

    @JsonProperty("node_selector")
    private List<CoreNodeSelector> nodeSelector;

    private CoreAffinity affinity;

    private List<CoreToleration> tolerations;

    private String runtimeClass;

    private String priorityClass;

    private CoreImagePullPolicy imagePullPolicy;

    //securityContext
    private Integer runAsUser;
    private Integer runAsGroup;
    private Integer fsGroup;

    private List<CoreLabel> labels;

    private String template;

    private Map<String, String> customResources;
    private Map<String, String> startParams;

}
