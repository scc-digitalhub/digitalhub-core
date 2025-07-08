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

package it.smartcommunitylabdhub.core.queries.filters;

import com.fasterxml.jackson.annotation.JsonIgnore;
import it.smartcommunitylabdhub.commons.models.queries.SearchCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BaseEntitySearchCriteria<T> implements SearchCriteria<T> {

    @NotBlank
    private String field;

    @NotBlank
    private Serializable value;

    @NotNull
    private Operation operation;

    @Override
    @Nullable
    @JsonIgnore
    public Predicate toPredicate(
        @NonNull Root<T> root,
        @NonNull CriteriaQuery<?> query,
        @NonNull CriteriaBuilder builder
    ) {
        switch (operation) {
            case Operation.equal:
                return builder.equal(root.get(field), value);
            case Operation.like:
                if (root.get(field).getJavaType() == String.class && value instanceof String) {
                    if (value instanceof String) {
                        //case insensitive by lowering both strings
                        return builder.like(
                            builder.lower(root.<String>get(field)),
                            "%" + ((String) value).toLowerCase() + "%"
                        );
                    }

                    //base like comparison
                    return builder.like(root.<String>get(field), "%" + value + "%");
                }
                break;
            case Operation.gt:
                if (root.get(field).getJavaType() == String.class) {
                    return builder.greaterThanOrEqualTo(root.get(field), value.toString());
                }
                if (root.get(field).getJavaType() == Date.class && value instanceof Date) {
                    return builder.greaterThanOrEqualTo(root.get(field), (Date) value);
                }
                if (root.get(field).getJavaType() == Integer.class && value instanceof Integer) {
                    return builder.greaterThanOrEqualTo(root.get(field), (Integer) value);
                }
                if (root.get(field).getJavaType() == Long.class && value instanceof Long) {
                    return builder.greaterThanOrEqualTo(root.get(field), (Long) value);
                }
                break;
            case Operation.lt:
                if (root.get(field).getJavaType() == String.class) {
                    return builder.lessThanOrEqualTo(root.get(field), value.toString());
                }
                if (root.get(field).getJavaType() == Date.class && value instanceof Date) {
                    return builder.lessThanOrEqualTo(root.get(field), (Date) value);
                }
                if (root.get(field).getJavaType() == Integer.class && value instanceof Integer) {
                    return builder.lessThanOrEqualTo(root.get(field), (Integer) value);
                }
                if (root.get(field).getJavaType() == Long.class && value instanceof Long) {
                    return builder.lessThanOrEqualTo(root.get(field), (Long) value);
                }
            default:
                return null;
        }

        return null;
    }

    @JsonIgnore
    public Specification<T> toSpecification() {
        return this;
    }
}
