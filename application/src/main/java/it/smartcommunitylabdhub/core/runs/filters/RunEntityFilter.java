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

package it.smartcommunitylabdhub.core.runs.filters;

import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.models.queries.SearchCriteria;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.core.queries.filters.AbstractEntityFilter;
import it.smartcommunitylabdhub.core.queries.filters.BaseEntityFilter;
import it.smartcommunitylabdhub.core.queries.filters.BaseEntitySearchCriteria;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RunEntityFilter extends AbstractEntityFilter<Run> {

    @Nullable
    @Pattern(regexp = Keys.TASK_PATTERN)
    @Schema(example = "kind://my-project/task-id", defaultValue = "", description = "Task key")
    private String task;

    @Nullable
    @Pattern(regexp = Keys.FUNCTION_PATTERN + "|" + Keys.TASK_PATTERN)
    @Schema(example = "kind://my-project/function-name:id", defaultValue = "", description = "Function key")
    private String function;

    @Nullable
    @Pattern(regexp = Keys.WORKFLOW_PATTERN + "|" + Keys.TASK_PATTERN)
    @Schema(example = "kind://my-project/workflow-name:id", defaultValue = "", description = "Workflow key")
    private String workflow;

    @Override
    public SearchFilter<Run> toSearchFilter() {
        List<SearchCriteria<Run>> criteria = new ArrayList<>();

        //base criteria
        criteria.addAll(super.toSearchFilter().getCriteria());

        //if name replace with id like + task like
        criteria
            .stream()
            .filter(c -> c.getField().equals("name"))
            .findFirst()
            .ifPresent(c -> {
                criteria.remove(c);
                criteria.add(new BaseEntitySearchCriteria<>("id", c.getValue(), SearchCriteria.Operation.like));
            });

        //task exact match
        Optional
            .ofNullable(task)
            .ifPresent(value ->
                criteria.add(new BaseEntitySearchCriteria<>("task", value, SearchCriteria.Operation.equal))
            );

        //function match
        Optional
            .ofNullable(function)
            .ifPresent(value -> {
                Matcher matcher = java.util.regex.Pattern.compile(Keys.FUNCTION_PATTERN).matcher(value);
                if (matcher.matches()) {
                    //exact match
                    criteria.add(new BaseEntitySearchCriteria<>("function", value, SearchCriteria.Operation.equal));
                } else {
                    //like match
                    criteria.add(new BaseEntitySearchCriteria<>("function", value, SearchCriteria.Operation.like));
                }
            });

        //workflow exact match
        Optional
            .ofNullable(workflow)
            .ifPresent(value -> {
                Matcher matcher = java.util.regex.Pattern.compile(Keys.WORKFLOW_PATTERN).matcher(value);
                if (matcher.matches()) {
                    //exact match
                    criteria.add(new BaseEntitySearchCriteria<>("workflow", value, SearchCriteria.Operation.equal));
                } else {
                    //like match
                    criteria.add(new BaseEntitySearchCriteria<>("workflow", value, SearchCriteria.Operation.like));
                }
            });

        return BaseEntityFilter.<Run>builder().criteria(criteria).condition(SearchFilter.Condition.and).build();
    }
}
