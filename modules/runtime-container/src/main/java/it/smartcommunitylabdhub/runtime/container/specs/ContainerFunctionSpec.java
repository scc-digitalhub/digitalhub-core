package it.smartcommunitylabdhub.runtime.container.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.function.FunctionBaseSpec;
import it.smartcommunitylabdhub.commons.models.objects.SourceCode;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreImagePullPolicy;
import it.smartcommunitylabdhub.runtime.container.ContainerRuntime;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = ContainerRuntime.RUNTIME, kind = ContainerRuntime.RUNTIME, entity = EntityName.FUNCTION)
public class ContainerFunctionSpec extends FunctionBaseSpec {

    @Schema(title = "fields.container.image.title", description = "fields.container.image.description")
    private String image;

    @Schema(title = "fields.container.baseImage.title", description = "fields.container.baseImage.description")
    @JsonProperty("base_image")
    private String baseImage;

    @Schema(
        title = "fields.container.imagePullPolicy.title",
        description = "fields.container.imagePullPolicy.description"
    )
    @JsonProperty("image_pull_policy")
    private CoreImagePullPolicy imagePullPolicy;

    @Schema(title = "fields.container.command.title", description = "fields.container.command.description")
    private String command;

    @Schema(title = "fields.sourceCode.title", description = "fields.sourceCode.description")
    private SourceCode<SourceCodeLanguages> source;

    public ContainerFunctionSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        ContainerFunctionSpec spec = mapper.convertValue(data, ContainerFunctionSpec.class);

        this.command = spec.getCommand();
        this.image = spec.getImage();
        this.baseImage = spec.getBaseImage();
        this.source = spec.getSource();
    }

    public enum SourceCodeLanguages {
        python,
        java,
        javascript,
        typescript,
        markdown,
        html,
        json,
        sql,
        css,
        yaml,
        text,
    }
}
