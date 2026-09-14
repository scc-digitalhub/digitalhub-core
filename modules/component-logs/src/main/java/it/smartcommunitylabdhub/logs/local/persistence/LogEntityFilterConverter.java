/**
 * Copyright 2025 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.logs.local.persistence;

import it.smartcommunitylabdhub.commons.models.queries.SearchCriteria;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter.Condition;
import it.smartcommunitylabdhub.core.queries.filters.BaseEntityFilter;
import it.smartcommunitylabdhub.core.queries.filters.BaseEntityFilter.BaseEntityFilterBuilder;
import it.smartcommunitylabdhub.core.queries.filters.BaseEntitySearchCriteria;
import it.smartcommunitylabdhub.logs.Log;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@Slf4j
public class LogEntityFilterConverter implements Converter<SearchFilter<Log>, SearchFilter<LogEntity>> {

    public static final String[] FIELDS = { "id", "project", "run" };

    protected String map(@NotNull String source) {
        if (Arrays.asList(FIELDS).contains(source)) {
            return source;
        }

        if ("user".equals(source)) {
            return "createdBy";
        }

        return null;
    }

    @Override
    @Nullable
    public SearchFilter<LogEntity> convert(SearchFilter<Log> filter) {
        //map fields and then convert to spec
        BaseEntityFilterBuilder<LogEntity> builder = BaseEntityFilter.<LogEntity>builder();
        builder.condition(filter.getCondition() != null ? filter.getCondition() : Condition.and);

        if (filter.getCriteria() != null) {
            List<SearchCriteria<LogEntity>> criteria = new ArrayList<>();

            filter
                .getCriteria()
                .forEach(c -> {
                    criteria.add(
                        new BaseEntitySearchCriteria<LogEntity>(map(c.getField()), c.getValue(), c.getOperation())
                    );
                });

            builder.criteria(criteria);
        }

        if (filter.getFilters() != null) {
            List<SearchFilter<LogEntity>> filters = new ArrayList<>();

            filter
                .getFilters()
                .forEach(f -> {
                    //recursively convert
                    filters.add(this.convert(f));
                });

            builder.filters(filters);
        }

        BaseEntityFilter<LogEntity> ef = builder.build();
        if (log.isTraceEnabled()) {
            log.trace("filter: {}", ef);
        }

        return ef;
    }
}
