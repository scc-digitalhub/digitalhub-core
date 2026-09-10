package it.smartcommunitylabdhub.compliance.model;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Condition asserting a metric value belongs to an allowed set of categories. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CategoricalCondition {

    @Schema(title = "fields.compliance.objective.condition.categorical.allowed.title", description = "fields.compliance.objective.condition.categorical.allowed.description")
    private Set<String> allowed;
}
