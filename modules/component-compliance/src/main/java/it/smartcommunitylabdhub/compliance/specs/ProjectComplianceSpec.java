package it.smartcommunitylabdhub.compliance.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.Map;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.compliance.model.ComplianceContext;
import it.smartcommunitylabdhub.compliance.model.DomainDescriptor;
import it.smartcommunitylabdhub.compliance.model.ProjectScope;
import it.smartcommunitylabdhub.extensions.annotations.ExtensionType;
import it.smartcommunitylabdhub.extensions.model.Extension;
import it.smartcommunitylabdhub.projects.Project;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@SpecType(kind = "ai-compliance", entity = Extension.class)
@ExtensionType(appliesTo = { Project.class })
@JsonPropertyOrder({
    "ai_task",
    "ai_task_value",
    "goal",
    "purpose",
    "domain",
    "deployment",
    "audience",
    "scope",
})

public class ProjectComplianceSpec extends BaseComplianceSpec {
    
    @NotNull
    @Schema(title = "fields.compliance.project.domain.title", description = "fields.compliance.project.domain.description")
    private DomainDescriptor domain;

    @NotNull
    @Schema(title = "fields.compliance.project.ai_task.title", description = "fields.compliance.project.ai_task.description")
    @JsonProperty("ai_task")
    private AiTask aiTask;
    @Schema(title = "fields.compliance.project.ai_task_value.title", description = "fields.compliance.project.ai_task_value.description")
    @JsonProperty("ai_task_value")
    private String aiTaskValue; // for custom tasks not in the enum

    /** High-level goal of the AI application. */
    @NotNull
    @Schema(title = "fields.compliance.project.goal.title", description = "fields.compliance.project.goal.description")
    private String goal;

    /** Intended function or business capability. */
    @Schema(title = "fields.compliance.project.purpose.title", description = "fields.compliance.project.purpose.description")
    private String purpose;
    
    /** e.g., "internal analysts", "end consumers". */
    @Schema(title = "fields.compliance.project.audience.title", description = "fields.compliance.project.audience.description")
    private String audience;
    
    @Schema(title = "fields.compliance.project.scope.title", description = "fields.compliance.project.scope.description")
    private ProjectScope scope;

    @Schema(title = "fields.compliance.project.deployment.title", description = "fields.compliance.project.deployment.description")
    private DeploymentKind deployment;

    @Schema(title = "fields.compliance.project.context.title", description = "fields.compliance.project.context.description")
    private ComplianceContext context;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);
        ProjectComplianceSpec spec = mapper.convertValue(data, ProjectComplianceSpec.class);
        this.domain = spec.getDomain();
        this.aiTask = spec.getAiTask();
        this.aiTaskValue = spec.getAiTaskValue();
        this.goal = spec.getGoal();
        this.purpose = spec.getPurpose();
        this.audience = spec.getAudience();
        this.scope = spec.getScope();
        this.deployment = spec.getDeployment();
        this.context = spec.getContext();
    }

    public enum AiTask {
        CLASSIFICATION,
        REGRESSION,
        RANKING,
        RECOMMENDATION,
        GENERATION,
        DETECTION,
        FORECASTING,
        CONTROL,
        DECISION_SUPPORT,
        OTHER,
    }

    public enum DeploymentKind {
        EDGE,
        PREMISE,
        CLOUD,
        EMBEDDED,
    }
}
