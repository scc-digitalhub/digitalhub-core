package it.smartcommunitylabdhub.runtime.kubeai.specs;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.base.K8sResourceProfileAware;
import it.smartcommunitylabdhub.runtime.kubeai.KubeAIServeRuntime;
import it.smartcommunitylabdhub.runtime.kubeai.models.KubeAIFile;
import it.smartcommunitylabdhub.runtime.kubeai.models.KubeAILoadBalancing;
import it.smartcommunitylabdhub.runtime.kubeai.models.KubeAIScaling;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = KubeAIServeRuntime.RUNTIME, kind = KubeAIServeRunSpec.KIND, entity = EntityName.RUN)
public class KubeAIServeRunSpec extends RunBaseSpec implements K8sResourceProfileAware {

    public static final String KIND = KubeAIServeRuntime.RUNTIME + "+run";

    @JsonSchemaIgnore
    @JsonUnwrapped
    private KubeAIServeFunctionSpec functionSpec;

    @JsonUnwrapped
    private KubeAIServeTaskSpec taskServeSpec;

    // execution
    @Schema(title = "fields.kubeai.args.title", description = "fields.kubeai.args.description")
    private List<String> args;
    @Schema(title = "fields.kubeai.env.title", description = "fields.kubeai.env.description")
    private Map<String, String> env;
    @Schema(title = "fields.kubeai.files.title", description = "fields.kubeai.files.description")
    private List<KubeAIFile> files;

    private Set<String> secrets;

    // resources
    @Schema(title = "fields.kubeai.resourceprofile.title", description = "fields.kubeai.resourceprofile.description")
    private String profile;
    @Schema(title = "fields.kubeai.cacheprofile.title", description = "fields.kubeai.cacheprofile.description")
    private String cacheProfile;

    @Schema(title = "fields.kubeai.scaling.title", description = "fields.kubeai.scaling.description")
    private KubeAIScaling scaling = new KubeAIScaling();

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);
        KubeAIServeRunSpec spec = mapper.convertValue(data, KubeAIServeRunSpec.class);
        this.functionSpec = spec.getFunctionSpec();
        this.taskServeSpec = spec.getTaskServeSpec();
        this.args = spec.getArgs();
        this.profile = spec.getProfile();
        this.cacheProfile = spec.getCacheProfile();
        this.env = spec.getEnv();
        this.scaling = spec.getScaling();
        this.files = spec.getFiles();   
        this.secrets = spec.getSecrets();
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
