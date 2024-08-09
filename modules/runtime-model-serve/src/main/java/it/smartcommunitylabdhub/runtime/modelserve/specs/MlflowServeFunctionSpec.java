package it.smartcommunitylabdhub.runtime.modelserve.specs;

import java.io.Serializable;
import java.util.Map;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import it.smartcommunitylabdhub.runtime.modelserve.MlflowServeRuntime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = MlflowServeRuntime.RUNTIME, kind = MlflowServeRuntime.RUNTIME, entity = EntityName.FUNCTION)
public class MlflowServeFunctionSpec extends ModelServeFunctionSpec {


    public static MlflowServeFunctionSpec with(Map<String, Serializable> data) {
        MlflowServeFunctionSpec spec = new MlflowServeFunctionSpec();
        spec.configure(data);
        return spec;
    }

}
