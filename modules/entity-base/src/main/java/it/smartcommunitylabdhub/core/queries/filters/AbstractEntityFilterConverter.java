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

package it.smartcommunitylabdhub.core.queries.filters;

import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.queries.SearchCriteria;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter.Condition;
import it.smartcommunitylabdhub.core.persistence.AbstractEntity_;
import it.smartcommunitylabdhub.core.persistence.BaseEntity;
import it.smartcommunitylabdhub.core.queries.filters.BaseEntityFilter.BaseEntityFilterBuilder;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;

@Slf4j
public class AbstractEntityFilterConverter<D extends BaseDTO, E extends BaseEntity>
    implements Converter<SearchFilter<D>, SearchFilter<E>> {

    public static final String[] FIELDS = {
        AbstractEntity_.CREATED,
        AbstractEntity_.CREATED_BY,
        AbstractEntity_.ID,
        AbstractEntity_.KIND,
        AbstractEntity_.PROJECT,
        AbstractEntity_.UPDATED,
        AbstractEntity_.UPDATED_BY,
        "name",
        "state",
    };

    private static final List<String> fields;

    static {
        fields = Arrays.asList(FIELDS);
    }

    @Override
    public SearchFilter<E> convert(SearchFilter<D> filter) {
        //map fields and then convert to spec
        BaseEntityFilterBuilder<E> builder = BaseEntityFilter.<E>builder();
        builder.condition(filter.getCondition() != null ? filter.getCondition() : Condition.and);

        if (filter.getCriteria() != null) {
            List<SearchCriteria<E>> criteria = new ArrayList<>();

            filter
                .getCriteria()
                .forEach(c -> {
                    criteria.add(new BaseEntitySearchCriteria<E>(map(c.getField()), c.getValue(), c.getOperation()));
                });

            builder.criteria(criteria);
        }

        if (filter.getFilters() != null) {
            List<SearchFilter<E>> filters = new ArrayList<>();

            filter
                .getFilters()
                .forEach(f -> {
                    //recursively convert
                    filters.add(this.convert(f));
                });

            builder.filters(filters);
        }

        BaseEntityFilter<E> ef = builder.build();
        if (log.isTraceEnabled()) {
            log.trace("filter: {}", ef);
        }

        return ef;
    }

    protected String map(@NotNull String source) {
        //map 1-1 for basic between entity and dto
        if (fields.contains(source)) {
            return source;
        }

        //user means creator
        if ("user".equals(source)) {
            return AbstractEntity_.CREATED_BY;
        }

        //status.state is top level
        if ("status.state".equals(source)) {
            return "state";
        }

        //to be overriden by descendant for specific fields
        throw new IllegalArgumentException();
    }
}
