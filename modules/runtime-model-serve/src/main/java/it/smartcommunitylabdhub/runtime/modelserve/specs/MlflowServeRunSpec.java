package it.smartcommunitylabdhub.runtime.modelserve.specs;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import it.smartcommunitylabdhub.runtime.modelserve.MlflowServeRuntime;

import java.io.Serializable;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = MlflowServeRuntime.RUNTIME, kind = MlflowServeRunSpec.KIND, entity = EntityName.RUN)
public class MlflowServeRunSpec extends RunBaseSpec {

    public static final String KIND = MlflowServeRuntime.RUNTIME + "+run";

    @JsonUnwrapped
    private MlflowServeFunctionSpec functionSpec;

    @JsonUnwrapped
    private MlflowServeTaskSpec taskServeSpec;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);
        MlflowServeRunSpec spec = mapper.convertValue(data, MlflowServeRunSpec.class);
        this.functionSpec = spec.getFunctionSpec();
        this.taskServeSpec = spec.getTaskServeSpec();
    }

    public void setFunctionSpec(MlflowServeFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }


    public void setTaskServeSpec(MlflowServeTaskSpec taskServeSpec) {
        this.taskServeSpec = taskServeSpec;
    }

    public static MlflowServeRunSpec with(Map<String, Serializable> data) {
        MlflowServeRunSpec spec = new MlflowServeRunSpec();
        spec.configure(data);
        return spec;
    }
}
