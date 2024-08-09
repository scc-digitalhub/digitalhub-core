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
@SpecType(runtime = MlflowServeRuntime.RUNTIME, kind = MlflowServeTaskSpec.KIND, entity = EntityName.TASK)
public class MlflowServeTaskSpec extends ModelServeServeTaskSpec {

    public static final String KIND = "mlflowserve+serve";


    public static MlflowServeTaskSpec with(Map<String, Serializable> data) {
        MlflowServeTaskSpec spec = new MlflowServeTaskSpec();
        spec.configure(data);
        return spec;
    }

}
