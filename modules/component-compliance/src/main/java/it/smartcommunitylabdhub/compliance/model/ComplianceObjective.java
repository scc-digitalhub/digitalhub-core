package it.smartcommunitylabdhub.compliance.model;

import java.util.HashSet;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplianceObjective {

    @Schema(title = "fields.compliance.objective.name.title", description = "fields.compliance.objective.name.description")
    private String name;
    @Schema(title = "fields.compliance.objective.description.title", description = "fields.compliance.objective.description.description")
    private String description;


    // measured through
    @Schema(title = "fields.compliance.objective.metric.title", description = "fields.compliance.objective.metric.description")
    private Metric metric;
    // expected condition
    @Schema(title = "fields.compliance.objective.condition.title", description = "fields.compliance.objective.condition.description")
    private ObjectiveCondition condition;
    @Schema(title = "fields.compliance.objective.priority.title", description = "fields.compliance.objective.priority.description")
    private Priority priority;
    @Schema(title = "fields.compliance.objective.severity.title", description = "fields.compliance.objective.severity.description")
    private Severity severity;
    
    @Schema(title = "fields.compliance.objective.requirements.title", description = "fields.compliance.objective.requirements.description")
    @Builder.Default
    private Set<String> requirements = new HashSet<>();

    public enum Priority {
        MANDATORY,
        RECOMMENDED,
        INFORMATIONAL,
    }

    public enum Severity {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW,
        INFO,
    }

}
