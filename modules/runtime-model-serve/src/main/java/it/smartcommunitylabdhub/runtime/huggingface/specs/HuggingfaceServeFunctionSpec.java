package it.smartcommunitylabdhub.runtime.huggingface.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.function.FunctionBaseSpec;
import it.smartcommunitylabdhub.runtime.huggingface.HuggingfaceServeRuntime;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
@SpecType(
    runtime = HuggingfaceServeRuntime.RUNTIME,
    kind = HuggingfaceServeRuntime.RUNTIME,
    entity = EntityName.FUNCTION
)
public class HuggingfaceServeFunctionSpec extends FunctionBaseSpec {

    @JsonProperty("path")
    @NotNull
    @Pattern(
        regexp = "^(store://([^/]+)/model/huggingface/.*)" +
        "|" +
        Keys.FOLDER_PATTERN +
        "|" +
        Keys.ZIP_PATTERN +
        "|" +
        "^huggingface?://.*$" +
        "|" +
        "^hf?://.*$"
    )
    @Schema(title = "fields.path.title", description = "fields.huggingface.path.description")
    private String path;

    @JsonProperty("model_name")
    @Schema(
        title = "fields.modelserve.modelname.title",
        description = "fields.modelserve.modelname.description",
        defaultValue = "model"
    )
    private String modelName;

    @JsonProperty("image")
    @Pattern(regexp = "^kserve\\/huggingfaceserver?:")
    @Schema(title = "fields.container.image.title", description = "fields.container.image.description")
    private String image;

    // @JsonProperty("adapters")
    // @Schema(title = "fields.huggingface.adapters.title", description = "fields.huggingface.adapters.description")
    // private Map<String, String> adapters;

    // @Override
    // public void configure(Map<String, Serializable> data) {
    //     super.configure(data);

    //     HuggingfaceServeFunctionSpec spec = mapper.convertValue(data, HuggingfaceServeFunctionSpec.class);
    //     this.adapters = spec.getAdapters();
    // }    @Override

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        HuggingfaceServeFunctionSpec spec = mapper.convertValue(data, HuggingfaceServeFunctionSpec.class);
        this.modelName = spec.getModelName();
        this.path = spec.getPath();
        this.image = spec.getImage();
    }

    public static HuggingfaceServeFunctionSpec with(Map<String, Serializable> data) {
        HuggingfaceServeFunctionSpec spec = new HuggingfaceServeFunctionSpec();
        spec.configure(data);
        return spec;
    }
}
