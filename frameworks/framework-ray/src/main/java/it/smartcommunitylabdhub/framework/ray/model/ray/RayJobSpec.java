package it.smartcommunitylabdhub.framework.ray.model.ray;


import java.io.Serializable;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RayJobSpec {

    private Integer activeDeadlineSeconds;
    private Integer backoffLimit;
    private RayClusterSpec rayClusterSpec;
    private Map<String, String> clusterSelector;
    // submitter ?

    private V1PodTemplateSpec submitterPodTemplate;

    private Boolean shutdownAfterJobFinishes;
    private Integer ttlSecondsAfterFinished;
    private Integer preRunningDeadlineSeconds;

    private String entrypoint;
    private String runtimeEnvYAML;
    private String jobId;
    private JobSubmissionMode submissionMode;
}
