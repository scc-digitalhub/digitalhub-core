package it.smartcommunitylabdhub.runtime.dbt.specs;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.runtime.dbt.DbtRuntime;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = DbtRuntime.RUNTIME, kind = DbtTransformSpec.KIND, entity = EntityName.TASK)
public class DbtTransformSpec extends K8sFunctionTaskBaseSpec {

    public static final String KIND = "dbt+transform";

    public DbtTransformSpec(Map<String, Serializable> data) {
        configure(data);
    }
}
