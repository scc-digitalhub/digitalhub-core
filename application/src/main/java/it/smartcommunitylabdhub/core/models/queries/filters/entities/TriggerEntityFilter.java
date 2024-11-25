package it.smartcommunitylabdhub.core.models.queries.filters.entities;

import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.Fields;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.models.queries.SearchCriteria;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.core.models.base.BaseEntityFilter;
import it.smartcommunitylabdhub.core.models.base.BaseEntitySearchCriteria;
import it.smartcommunitylabdhub.core.models.entities.TriggerEntity;
import it.smartcommunitylabdhub.core.models.queries.filters.abstracts.AbstractEntityFilter;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TriggerEntityFilter extends AbstractEntityFilter<TriggerEntity> {

    @Nullable
    @Pattern(regexp = Keys.TASK_PATTERN)
    @Schema(example = "kind://my-project/kind/task-id", defaultValue = "", description = "Task path")
    private String task;

    @Override
    public SearchFilter<TriggerEntity> toSearchFilter() {
        List<SearchCriteria<TriggerEntity>> criteria = new ArrayList<>();

        //base criteria
        criteria.addAll(super.toSearchFilter().getCriteria());

        //task exact match
        Optional
            .ofNullable(task)
            .ifPresent(value ->
                criteria.add(new BaseEntitySearchCriteria<>(Fields.TASK, value, SearchCriteria.Operation.equal))
            );

        return BaseEntityFilter
            .<TriggerEntity>builder()
            .criteria(criteria)
            .condition(SearchFilter.Condition.and)
            .build();
    }
}
