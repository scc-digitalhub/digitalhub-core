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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;

@Slf4j
public class AbstractEntityFilterConverter<D extends BaseDTO, E extends BaseEntity>
    implements Converter<SearchFilter<D>, SearchFilter<E>> {

    private final List<String> fields;

    protected AbstractEntityFilterConverter() {
        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        Class<D> type = (Class<D>) t;
        Type t2 = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[1];
        Class<E> clazz = (Class<E>) t2;
        //enumerate all commond fields to enable 1-1 mapping by default
        List<String> typeFields = enumerateFields(type);
        List<String> clazzFields = enumerateFields(clazz);

        //pick only common fields to avoid mapping issues
        List<String> common = new ArrayList<>();
        if (typeFields != null && clazzFields != null) {
            clazzFields.forEach(f -> {
                if (typeFields.contains(f)) {
                    common.add(f);
                }
            });
        }

        this.fields = Collections.unmodifiableList(common);
    }

    public AbstractEntityFilterConverter(Class<D> type, Class<E> clazz) {
        Assert.notNull(type, "type is required");
        Assert.notNull(clazz, "clazz is required");

        //enumerate all commond fields to enable 1-1 mapping by default
        List<String> typeFields = enumerateFields(type);
        List<String> clazzFields = enumerateFields(clazz);

        //pick only common fields to avoid mapping issues
        List<String> common = new ArrayList<>();
        if (typeFields != null && clazzFields != null) {
            clazzFields.forEach(f -> {
                if (typeFields.contains(f)) {
                    common.add(f);
                }
            });
        }

        this.fields = Collections.unmodifiableList(common);
    }

    public AbstractEntityFilterConverter(List<String> fields) {
        this.fields = fields != null ? Collections.unmodifiableList(fields) : Collections.emptyList();
    }

    @Override
    public SearchFilter<E> convert(@NonNull SearchFilter<D> filter) {
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

    private List<String> enumerateFields(Class<?> clazz) {
        if (clazz == null || clazz.equals(Object.class)) {
            return null;
        }
        // All fields including inherited (walk the hierarchy)
        List<String> names = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            names.addAll(
                Arrays
                    .stream(current.getDeclaredFields())
                    .filter(f -> !Modifier.isStatic(f.getModifiers()) && !f.isSynthetic())
                    .filter(f -> COMPARABLE_TYPES.contains(f.getType()))
                    .map(Field::getName)
                    .toList()
            );

            current = current.getSuperclass();
        }

        return names;
    }

    private static final Set<Class<?>> COMPARABLE_TYPES = Set.of(
        boolean.class,
        Boolean.class,
        byte.class,
        Byte.class,
        short.class,
        Short.class,
        int.class,
        Integer.class,
        long.class,
        Long.class,
        float.class,
        Float.class,
        double.class,
        Double.class,
        char.class,
        Character.class,
        String.class
    );
}
