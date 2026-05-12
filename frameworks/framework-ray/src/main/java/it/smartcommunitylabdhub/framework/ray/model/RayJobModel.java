package it.smartcommunitylabdhub.framework.ray.model;

import java.io.Serializable;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

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
public class RayJobModel extends RayModel {

    private Map<String, String> clusterSelector;

    private String entrypoint;

    @JsonProperty("dependency_format")
    private String dependencyFormat;

    @JsonProperty("dependency_spec")
    private Serializable dependencySpec;
    
    private Integer backoffLimit;
    
    private Boolean shutdownAfterJobFinishes;
    private Integer ttlSecondsAfterFinished;
    private Integer preRunningDeadlineSeconds;
}
