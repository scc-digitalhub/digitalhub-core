package it.smartcommunitylabdhub.runtime.python.specs.function;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.function.FunctionBaseSpec;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import it.smartcommunitylabdhub.commons.models.objects.SourceCode;
import it.smartcommunitylabdhub.runtime.python.PythonRuntime;
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
@SpecType(runtime = PythonRuntime.RUNTIME, kind = PythonRuntime.RUNTIME, entity = EntityName.FUNCTION)
public class PythonFunctionSpec extends FunctionBaseSpec {

    @JsonProperty("source")
    @NotNull
    private SourceCode<PythonSourceCodeLanguages> source;

    @JsonProperty("image")
    @Schema(title = "fields.containerImage.title", description = "fields.containerImage.description")
    private String image;

    @JsonProperty("base_image")
    @Schema(title = "fields.containerBaseImage.title", description = "fields.containerBaseImage.description")
    private String baseImage;

    @Schema(title = "fields.pythonRequirements.title", description = "fields.pythonRequirements.description")
    private List<String> requirements;

    public PythonFunctionSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        PythonFunctionSpec spec = mapper.convertValue(data, PythonFunctionSpec.class);

        this.source = spec.getSource();
        this.image = spec.getImage();
        this.baseImage = spec.getBaseImage();
        this.requirements = spec.getRequirements();
    }

    public enum PythonSourceCodeLanguages {
        python,
    }
}