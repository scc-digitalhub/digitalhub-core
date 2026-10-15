package it.smartcommunitylabdhub.compliance.model;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Condition combining multiple sub-conditions with a logical operator. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CompositeCondition{

    @Schema(title = "fields.compliance.objective.condition.composite.operator.title", description = "fields.compliance.objective.condition.composite.operator.description")
    private LogicalOperator operator;
    @Schema(title = "fields.compliance.objective.condition.composite.conditions.title", description = "fields.compliance.objective.condition.composite.conditions.description")
    private List<ObjectiveCondition> conditions;

    public enum LogicalOperator {
        AND,
        OR,
    }
}
