package it.smartcommunitylabdhub.runtime.kubeai.specs;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.function.FunctionTaskBaseSpec;
import it.smartcommunitylabdhub.runtime.kubeai.KubeAIServeRuntime;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = KubeAIServeRuntime.RUNTIME, kind = KubeAIServeTaskSpec.KIND, entity = EntityName.TASK)
public class KubeAIServeTaskSpec extends FunctionTaskBaseSpec {

    public static final String KIND = KubeAIServeRuntime.RUNTIME + "+serve";

    public static KubeAIServeTaskSpec with(Map<String, Serializable> data) {
        KubeAIServeTaskSpec spec = new KubeAIServeTaskSpec();
        spec.configure(data);
        return spec;
    }
}
