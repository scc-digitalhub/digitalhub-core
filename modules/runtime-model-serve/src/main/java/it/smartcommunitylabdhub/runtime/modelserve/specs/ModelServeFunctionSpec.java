package it.smartcommunitylabdhub.runtime.modelserve.specs;

import it.smartcommunitylabdhub.commons.models.entities.function.FunctionBaseSpec;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ModelServeFunctionSpec extends FunctionBaseSpec {
    
    @JsonProperty("path")
    @NotNull
    @Schema(title = "fields.modelserve.path.title", description = "fields.modelserve.path.description")
    private String path;

    @JsonProperty("model_name")
    @Schema(title = "fields.modelserve.modelname.title", description = "fields.modelserve.modelname.description")
    private String modelName;

    @JsonProperty("image")
    @Schema(title = "fields.container.image.title", description = "fields.container.image.description")
    private String image;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        ModelServeFunctionSpec spec = mapper.convertValue(data, ModelServeFunctionSpec.class);

        this.modelName = spec.getModelName();
        this.path = spec.getPath();
        this.image = spec.getImage();
    }

}
