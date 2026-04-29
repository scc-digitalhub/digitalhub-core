package it.smartcommunitylabdhub.framework.ray.model;

import java.io.Serializable;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class RayJobModel {

    private ClusterModel cluster;
    private Map<String, String> clusterSelector;

    private String entrypoint;
    // TODO: expand?
    private Map<String, Serializable> runtimeEnv;

    private Integer backoffLimit;
    
    private Boolean shutdownAfterJobFinishes;
    private Integer ttlSecondsAfterFinished;
    private Integer preRunningDeadlineSeconds;
}
