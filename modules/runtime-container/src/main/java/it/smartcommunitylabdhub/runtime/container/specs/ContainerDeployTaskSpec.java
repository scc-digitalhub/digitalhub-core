package it.smartcommunitylabdhub.runtime.container.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.runtime.container.ContainerRuntime;
import jakarta.validation.constraints.Min;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = ContainerRuntime.RUNTIME, kind = ContainerDeployTaskSpec.KIND, entity = EntityName.TASK)
public class ContainerDeployTaskSpec extends K8sFunctionTaskBaseSpec {

    public static final String KIND = "container+deploy";

    @Min(1)
    private Integer replicas;

    @JsonProperty("fs_group")
    @Min(1)
    private Integer fsGroup;

    @JsonProperty("run_as_user")
    @Min(1)
    private Integer runAsUser;

    @JsonProperty("run_as_group")
    @Min(1)
    private Integer runAsGroup;

    public ContainerDeployTaskSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        ContainerDeployTaskSpec spec = mapper.convertValue(data, ContainerDeployTaskSpec.class);
        this.replicas = spec.getReplicas();
        this.fsGroup = spec.getFsGroup();
        this.runAsGroup = spec.getRunAsUser();
        this.runAsGroup = spec.getRunAsGroup();
    }
}
