package it.smartcommunitylabdhub.framework.ray.model.ray;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RayClusterSpec {

    // TODO: upgradeStrategy, authOptions, managedBy, headServiceAnnotations
    // TODO: autoscalerOptions, enableInTreeAutoscaling, gcsFaultToleranceOptions

    private Boolean suspend;
    private HeadGroupSpec headGroupSpec;
    private String rayVersion;
    private List<WorkerGroupSpec> workerGroupSpecs;
}
