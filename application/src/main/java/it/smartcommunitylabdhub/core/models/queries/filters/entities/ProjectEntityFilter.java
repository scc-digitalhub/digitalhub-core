package it.smartcommunitylabdhub.core.models.queries.filters.entities;

import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.queries.SearchCriteria;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.core.models.base.BaseEntityFilter;
import it.smartcommunitylabdhub.core.models.base.BaseEntitySearchCriteria;
import it.smartcommunitylabdhub.core.models.entities.ProjectEntity;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.StringUtils;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Valid
public class ProjectEntityFilter {

    @Nullable
    @Pattern(regexp = Keys.SLUG_PATTERN)
    @Schema(example = "my-project-1", defaultValue = "", description = "Name identifier")
    protected String name;

    @Nullable
    protected String state;

    @Nullable
    protected String created;

    @Nullable
    protected String updated;

    public SearchFilter<ProjectEntity> toSearchFilter() {
        //build default search fields in AND
        List<SearchCriteria<ProjectEntity>> criteria = new ArrayList<>();
        Optional
            .ofNullable(name)
            .ifPresent(value ->
                criteria.add(new BaseEntitySearchCriteria<>("name", value, SearchCriteria.Operation.like))
            );

        Optional
            .ofNullable(state)
            .ifPresent(value -> {
                try {
                    criteria.add(
                        new BaseEntitySearchCriteria<>("state", State.valueOf(value), SearchCriteria.Operation.equal)
                    );
                } catch (IllegalArgumentException e) {
                    //invalid enum value, skip
                }
            });

        Optional
            .ofNullable(created)
            .ifPresent(value -> {
                try {
                    //parse as comma-separated interval or single date
                    String[] dates = StringUtils.commaDelimitedListToStringArray(value);
                    LocalDateTime startDate = LocalDateTime.parse(dates[0], DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    criteria.add(
                        new BaseEntitySearchCriteria<>(
                            "created",
                            Date.from(startDate.atZone(ZoneId.systemDefault()).toInstant()),
                            SearchCriteria.Operation.gt
                        )
                    );

                    if (dates.length == 2) {
                        //interval start,end

                        LocalDateTime endDate = LocalDateTime.parse(dates[1], DateTimeFormatter.ISO_OFFSET_DATE_TIME);

                        criteria.add(
                            new BaseEntitySearchCriteria<>(
                                "created",
                                Date.from(endDate.atZone(ZoneId.systemDefault()).toInstant()),
                                SearchCriteria.Operation.lt
                            )
                        );
                    }
                } catch (DateTimeParseException e) {
                    //invalid dates, skip
                }
            });

        Optional
            .ofNullable(updated)
            .ifPresent(value -> {
                try {
                    //parse as comma-separated interval or single date
                    String[] dates = StringUtils.commaDelimitedListToStringArray(value);
                    LocalDateTime startDate = LocalDateTime.parse(dates[0], DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    criteria.add(
                        new BaseEntitySearchCriteria<>(
                            "updated",
                            Date.from(startDate.atZone(ZoneId.systemDefault()).toInstant()),
                            SearchCriteria.Operation.gt
                        )
                    );

                    if (dates.length == 2) {
                        //interval start,end

                        LocalDateTime endDate = LocalDateTime.parse(dates[1], DateTimeFormatter.ISO_OFFSET_DATE_TIME);

                        criteria.add(
                            new BaseEntitySearchCriteria<>(
                                "updated",
                                Date.from(endDate.atZone(ZoneId.systemDefault()).toInstant()),
                                SearchCriteria.Operation.lt
                            )
                        );
                    }
                } catch (DateTimeParseException e) {
                    //invalid dates, skip
                }
            });

        return BaseEntityFilter
            .<ProjectEntity>builder()
            .criteria(criteria)
            .condition(SearchFilter.Condition.and)
            .build();
    }
}
