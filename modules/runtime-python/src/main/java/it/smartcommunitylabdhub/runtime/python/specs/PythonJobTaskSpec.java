package it.smartcommunitylabdhub.runtime.python.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import it.smartcommunitylabdhub.framework.k8s.base.K8sTaskBaseSpec;
import it.smartcommunitylabdhub.runtime.python.PythonRuntime;
import jakarta.validation.constraints.Min;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SpecType(runtime = PythonRuntime.RUNTIME, kind = PythonJobTaskSpec.KIND, entity = EntityName.TASK)
public class PythonJobTaskSpec extends K8sTaskBaseSpec {

    public static final String KIND = "python+job";

    @JsonProperty("backoff_limit")
    @Min(0)
    private Integer backoffLimit;

    public PythonJobTaskSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        PythonJobTaskSpec spec = mapper.convertValue(data, PythonJobTaskSpec.class);
        this.backoffLimit = spec.getBackoffLimit();
    }
}
