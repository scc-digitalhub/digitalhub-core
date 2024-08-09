package it.smartcommunitylabdhub.runtime.modelserve.specs;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import it.smartcommunitylabdhub.runtime.modelserve.SklearnServeRuntime;

import java.io.Serializable;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = SklearnServeRuntime.RUNTIME, kind = SklearnServeRunSpec.KIND, entity = EntityName.RUN)
public class SklearnServeRunSpec extends RunBaseSpec {

    @JsonUnwrapped
    private SklearnServeFunctionSpec functionSpec;

    @JsonUnwrapped
    private SklearnServeTaskSpec taskServeSpec;

    public static final String KIND = SklearnServeRuntime.RUNTIME + "+run";

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        SklearnServeRunSpec spec = mapper.convertValue(data, SklearnServeRunSpec.class);

        this.functionSpec = spec.getFunctionSpec();
        this.taskServeSpec = spec.getTaskServeSpec();
    }

    public void setFunctionSpec(SklearnServeFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }


    public void setTaskServeSpec(SklearnServeTaskSpec taskServeSpec) {
        this.taskServeSpec = taskServeSpec;
    }
    public static SklearnServeRunSpec with(Map<String, Serializable> data) {
        SklearnServeRunSpec spec = new SklearnServeRunSpec();
        spec.configure(data);
        return spec;
    }
    

}
