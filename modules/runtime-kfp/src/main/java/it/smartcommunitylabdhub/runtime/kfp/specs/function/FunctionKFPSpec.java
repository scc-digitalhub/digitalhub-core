package it.smartcommunitylabdhub.runtime.kfp.specs.function;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.function.FunctionBaseSpec;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import it.smartcommunitylabdhub.commons.models.objects.SourceCode;
import it.smartcommunitylabdhub.runtime.kfp.KFPRuntime;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = KFPRuntime.RUNTIME, kind = KFPRuntime.RUNTIME, entity = EntityName.FUNCTION)
public class FunctionKFPSpec extends FunctionBaseSpec {

    @NotNull
    @Schema(description = "Source code")
    private SourceCode<KFPSourceCodeLanguages> source;

    @Schema(description = "Container image name")
    private String image;

    @Schema(description = "Container image tag")
    private String tag;

    @Schema(description = "Handler method inside the function")
    private String handler;

    @Schema(description = "Override the command run in the container")
    private String command;

    @Schema(description = "Requirements list, as used by the runtime")
    private List<Serializable> requirements;

    public FunctionKFPSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        FunctionKFPSpec spec = mapper.convertValue(data, FunctionKFPSpec.class);

        this.source = spec.getSource();
        this.image = spec.getImage();
        this.tag = spec.getTag();
        this.handler = spec.getHandler();
        this.command = spec.getCommand();
        this.requirements = spec.getRequirements();
    }

    public enum KFPSourceCodeLanguages {
        python,
    }
}
