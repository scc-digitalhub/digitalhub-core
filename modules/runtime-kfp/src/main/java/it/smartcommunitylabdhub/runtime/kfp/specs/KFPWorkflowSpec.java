package it.smartcommunitylabdhub.runtime.kfp.specs;

import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.objects.SourceCode;
import it.smartcommunitylabdhub.commons.models.workflow.WorkflowBaseSpec;
import it.smartcommunitylabdhub.runtime.kfp.KFPRuntime;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = KFPRuntime.RUNTIME, kind = KFPRuntime.RUNTIME, entity = EntityName.WORKFLOW)
public class KFPWorkflowSpec extends WorkflowBaseSpec {

    @NotNull
    @Schema(title = "fields.sourceCode.title", description = "fields.sourceCode.description")
    private SourceCode<KFPSourceCodeLanguages> source;

    @Schema(title = "fields.container.image.title", description = "fields.container.image.description")
    private String image;

    @Schema(title = "fields.container.tag.title", description = "fields.container.tag.description")
    private String tag;

    @JsonSchemaIgnore
    private SourceCode<KFPWorkflowCodeLanguages> build;

    public KFPWorkflowSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        KFPWorkflowSpec spec = mapper.convertValue(data, KFPWorkflowSpec.class);

        this.source = spec.getSource();
        this.image = spec.getImage();
        this.tag = spec.getTag();
        this.build = spec.getBuild();
    }

    public enum KFPSourceCodeLanguages {
        python,
    }

    public enum KFPWorkflowCodeLanguages {
        yaml,
    }
}
