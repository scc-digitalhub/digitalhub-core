/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package it.smartcommunitylabdhub.core.projects.filters;

import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.models.queries.SearchCriteria;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.core.queries.filters.BaseEntityFilter;
import it.smartcommunitylabdhub.core.queries.filters.BaseEntitySearchCriteria;
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

    @Nullable
    protected String user;

    public SearchFilter<Project> toSearchFilter() {
        //build default search fields in AND
        List<SearchCriteria<Project>> criteria = new ArrayList<>();
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

        Optional
            .ofNullable(user)
            .ifPresent(value ->
                criteria.add(new BaseEntitySearchCriteria<>("createdBy", value, SearchCriteria.Operation.equal))
            );

        return BaseEntityFilter.<Project>builder().criteria(criteria).condition(SearchFilter.Condition.and).build();
    }
}
