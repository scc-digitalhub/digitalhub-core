package it.smartcommunitylabdhub.runtime.container.specs.run;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import it.smartcommunitylabdhub.runtime.container.ContainerRuntime;
import it.smartcommunitylabdhub.runtime.container.specs.function.FunctionContainerSpec;
import it.smartcommunitylabdhub.runtime.container.specs.task.TaskDeploySpec;
import it.smartcommunitylabdhub.runtime.container.specs.task.TaskJobSpec;
import it.smartcommunitylabdhub.runtime.container.specs.task.TaskServeSpec;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = ContainerRuntime.RUNTIME, kind = RunContainerSpec.KIND, entity = EntityName.RUN)
public class RunContainerSpec extends RunBaseSpec {

    public static final String KIND = ContainerRuntime.RUNTIME + "+run";

    @JsonProperty("job_spec")
    private TaskJobSpec taskJobSpec;

    @JsonProperty("deploy_spec")
    private TaskDeploySpec taskDeploySpec;

    @JsonProperty("serve_spec")
    private TaskServeSpec taskServeSpec;

    @JsonProperty("function_spec")
    private FunctionContainerSpec functionSpec;

    public RunContainerSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        RunContainerSpec spec = mapper.convertValue(data, RunContainerSpec.class);

        this.taskJobSpec = spec.getTaskJobSpec();
        this.taskDeploySpec = spec.getTaskDeploySpec();
        this.taskServeSpec = spec.getTaskServeSpec();
        this.functionSpec = spec.getFunctionSpec();
    }
}
