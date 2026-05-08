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

package it.smartcommunitylabdhub.functions.filters;

import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.queries.SearchCriteria;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter.Condition;
import it.smartcommunitylabdhub.core.queries.filters.AbstractEntityFilter;
import it.smartcommunitylabdhub.core.queries.filters.BaseEntityFilter;
import it.smartcommunitylabdhub.core.queries.filters.BaseEntitySearchCriteria;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FunctionEntityFilter extends AbstractEntityFilter<Function> {

    @Nullable
    private List<String> labels;

    @Override
    public SearchFilter<Function> toSearchFilter() {
        List<SearchCriteria<Function>> criteria = new ArrayList<>();
        List<SearchFilter<Function>> filters = new ArrayList<>();

        //base criteria
        SearchFilter<Function> sf = super.toSearchFilter();
        criteria.addAll(sf.getCriteria());
        filters.addAll(sf.getFilters());

        //labels in AND
        Optional
            .ofNullable(labels)
            .ifPresent(value -> {
                List<SearchCriteria<Function>> lcr = new ArrayList<>();
                value.forEach(label ->
                    lcr.add(new BaseEntitySearchCriteria<>("labels", label, SearchCriteria.Operation.like))
                );

                BaseEntityFilter<Function> qf = BaseEntityFilter
                    .<Function>builder()
                    .condition(Condition.and)
                    .criteria(lcr)
                    .build();
                filters.add(qf);
            });

        return BaseEntityFilter
            .<Function>builder()
            .criteria(criteria)
            .filters(filters)
            .condition(SearchFilter.Condition.and)
            .build();
    }
}
