package it.smartcommunitylabdhub.runtime.kubeai.specs;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.function.FunctionBaseSpec;
import it.smartcommunitylabdhub.runtime.kubeai.KubeAIServeRuntime;
import it.smartcommunitylabdhub.runtime.kubeai.models.KubeAIAdapter;
import it.smartcommunitylabdhub.runtime.kubeai.models.KubeAIEngine;
import it.smartcommunitylabdhub.runtime.kubeai.models.KubeAIFeature;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SpecType(
    runtime = KubeAIServeRuntime.RUNTIME,
    kind = KubeAIServeRuntime.RUNTIME,
    entity = EntityName.FUNCTION
)
public class KubeAIServeFunctionSpec extends FunctionBaseSpec {

    @NotNull
    @Pattern(
        regexp = 
        "^(store://([^/]+)/model/huggingface/.*)" +
        "|" +
        "^pvc?://.*$" +
        "|" +
        "^s3?://.*$" +
        "|" +
        "^ollama?://.*$" +
        "|" +
        "^hf?://.*$"
    )
    @Schema(title = "fields.kubeai.url.title", description = "fields.kubeai.url.description")
    private String url;

    @JsonProperty("model_name")
    @Schema(
        title = "fields.modelserve.modelname.title",
        description = "fields.modelserve.modelname.description",
        defaultValue = "model"
    )
    private String modelName;

    @JsonProperty("image")
    @Schema(title = "fields.kubeai.image.title", description = "fields.kubeai.image.description")
    private String image;

    @JsonProperty("adapters")
    @Schema(title = "fields.kubeai.adapters.title", description = "fields.kubeai.adapters.description")
    private List<KubeAIAdapter> adapters;


    @JsonProperty("features")
    @Schema(title = "fields.kubeai.features.title", description = "fields.kubeai.features.description", requiredMode = Schema.RequiredMode.REQUIRED)
    @Builder.Default
    private List<KubeAIFeature> features = List.of(KubeAIFeature.TextGeneration);

    @JsonProperty("engine")
    @Schema(title = "fields.kubeai.engine.title", description = "fields.kubeai.engine.description", requiredMode = Schema.RequiredMode.REQUIRED)
    private KubeAIEngine engine;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        KubeAIServeFunctionSpec spec = mapper.convertValue(data, KubeAIServeFunctionSpec.class);
        this.modelName = spec.getModelName();
        this.image = spec.getImage();
        this.url = spec.getUrl();
        this.adapters = spec.getAdapters();
        this.features = spec.getFeatures();
        this.engine = spec.getEngine();
    }

    public static KubeAIServeFunctionSpec with(Map<String, Serializable> data) {
        KubeAIServeFunctionSpec spec = new KubeAIServeFunctionSpec();
        spec.configure(data);
        return spec;
    }
}
