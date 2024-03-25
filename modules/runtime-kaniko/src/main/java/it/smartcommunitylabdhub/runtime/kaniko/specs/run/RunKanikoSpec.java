package it.smartcommunitylabdhub.runtime.kaniko.specs.run;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import it.smartcommunitylabdhub.runtime.kaniko.KanikoRuntime;
import it.smartcommunitylabdhub.runtime.kaniko.specs.function.FunctionKanikoSpec;
import it.smartcommunitylabdhub.runtime.kaniko.specs.task.TaskBuildSpec;
import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = KanikoRuntime.RUNTIME, kind = RunKanikoSpec.KIND, entity = EntityName.RUN)
public class RunKanikoSpec extends RunBaseSpec {

    public static final String KIND = KanikoRuntime.RUNTIME + "+run";

    private List<Map.Entry<String, Serializable>> inputs = new LinkedList<>();

    private List<Map.Entry<String, Serializable>> outputs = new LinkedList<>();

    private Map<String, Serializable> parameters = new HashMap<>();

    @JsonProperty("transform_spec")
    private TaskBuildSpec taskSpec;

    @JsonProperty("function_spec")
    private FunctionKanikoSpec funcSpec;

    public RunKanikoSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        RunKanikoSpec spec = mapper.convertValue(data, RunKanikoSpec.class);
        this.inputs = spec.getInputs();
        this.outputs = spec.getOutputs();
        this.parameters = spec.getParameters();

        this.taskSpec = spec.getTaskSpec();
        this.funcSpec = spec.getFuncSpec();
    }
}
