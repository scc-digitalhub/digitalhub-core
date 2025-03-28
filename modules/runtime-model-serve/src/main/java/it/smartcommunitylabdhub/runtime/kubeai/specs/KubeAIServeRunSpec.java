package it.smartcommunitylabdhub.runtime.kubeai.specs;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.runtime.kubeai.KubeAIServeRuntime;
import it.smartcommunitylabdhub.runtime.kubeai.models.KubeAIFile;
import it.smartcommunitylabdhub.runtime.kubeai.models.KubeAILoadBalancing;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = KubeAIServeRuntime.RUNTIME, kind = KubeAIServeRunSpec.KIND, entity = EntityName.RUN)
public class KubeAIServeRunSpec extends RunBaseSpec {

    public static final String KIND = KubeAIServeRuntime.RUNTIME + "+run";

    @JsonSchemaIgnore
    @JsonUnwrapped
    private KubeAIServeFunctionSpec functionSpec;

    @JsonUnwrapped
    private KubeAIServeTaskSpec taskServeSpec;

    @Schema(title = "fields.kubeai.args.title", description = "fields.kubeai.args.description")
    private List<String> args;

    @Schema(title = "fields.kubeai.resourceprofile.title", description = "fields.kubeai.resourceprofile.description")
    private String resourceProfile;

    @Schema(title = "fields.kubeai.cacheprofile.title", description = "fields.kubeai.cacheprofile.description")
    private String cacheProfile;

    @Schema(title = "fields.kubeai.env.title", description = "fields.kubeai.env.description")
    private Map<String, String> env;

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

    @Schema(title = "fields.kubeai.files.title", description = "fields.kubeai.files.description")
    private List<KubeAIFile> files;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);
        KubeAIServeRunSpec spec = mapper.convertValue(data, KubeAIServeRunSpec.class);
        this.functionSpec = spec.getFunctionSpec();
        this.taskServeSpec = spec.getTaskServeSpec();
        this.args = spec.getArgs();
        this.resourceProfile = spec.getResourceProfile();
        this.cacheProfile = spec.getCacheProfile();
        this.env = spec.getEnv();
        this.replicas = spec.getReplicas();
        this.minReplicas = spec.getMinReplicas();
        this.maxReplicas = spec.getMaxReplicas();
        this.autoscalingDisabled = spec.getAutoscalingDisabled();
        this.targetRequests = spec.getTargetRequests();
        this.scaleDownDelaySeconds = spec.getScaleDownDelaySeconds();
        this.loadBalancing = spec.getLoadBalancing();
        this.files = spec.getFiles();   
    }

    public void setFunctionSpec(KubeAIServeFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }

    public void setTaskServeSpec(KubeAIServeTaskSpec taskServeSpec) {
        this.taskServeSpec = taskServeSpec;
    }

    public static KubeAIServeRunSpec with(Map<String, Serializable> data) {
        KubeAIServeRunSpec spec = new KubeAIServeRunSpec();
        spec.configure(data);
        return spec;
    }
}
