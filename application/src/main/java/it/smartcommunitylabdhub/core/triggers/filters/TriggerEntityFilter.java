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

package it.smartcommunitylabdhub.core.triggers.filters;

import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.Fields;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.models.queries.SearchCriteria;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.trigger.Trigger;
import it.smartcommunitylabdhub.core.queries.filters.AbstractEntityFilter;
import it.smartcommunitylabdhub.core.queries.filters.BaseEntityFilter;
import it.smartcommunitylabdhub.core.queries.filters.BaseEntitySearchCriteria;
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
public class TriggerEntityFilter extends AbstractEntityFilter<Trigger> {

    @Nullable
    @Pattern(regexp = Keys.TASK_PATTERN)
    @Schema(example = "kind://my-project/kind/task-id", defaultValue = "", description = "Task path")
    private String task;

    @Override
    public SearchFilter<Trigger> toSearchFilter() {
        List<SearchCriteria<Trigger>> criteria = new ArrayList<>();

        //base criteria
        criteria.addAll(super.toSearchFilter().getCriteria());

        //task exact match
        Optional
            .ofNullable(task)
            .ifPresent(value ->
                criteria.add(new BaseEntitySearchCriteria<>(Fields.TASK, value, SearchCriteria.Operation.equal))
            );

        return BaseEntityFilter.<Trigger>builder().criteria(criteria).condition(SearchFilter.Condition.and).build();
    }
}
