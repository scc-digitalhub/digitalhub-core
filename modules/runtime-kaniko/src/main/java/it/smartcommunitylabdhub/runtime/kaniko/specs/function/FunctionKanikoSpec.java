package it.smartcommunitylabdhub.runtime.kaniko.specs.function;

import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.function.FunctionBaseSpec;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import it.smartcommunitylabdhub.commons.models.objects.SourceCode;
import it.smartcommunitylabdhub.runtime.kaniko.KanikoRuntime;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = KanikoRuntime.RUNTIME, kind = KanikoRuntime.RUNTIME, entity = EntityName.FUNCTION)
public class FunctionKanikoSpec extends FunctionBaseSpec {

    @NotNull
    @Schema(description = "Source code for the kaniko function")
    private SourceCode<KanikoSourceCodeLanguages> source;

    public FunctionKanikoSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        FunctionKanikoSpec spec = mapper.convertValue(data, FunctionKanikoSpec.class);
        this.source = spec.getSource();
    }

    public enum KanikoSourceCodeLanguages {
        java,
        python,
    }
}
