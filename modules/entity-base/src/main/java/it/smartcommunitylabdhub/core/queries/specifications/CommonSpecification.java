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

package it.smartcommunitylabdhub.core.queries.specifications;

import it.smartcommunitylabdhub.commons.Fields;
import it.smartcommunitylabdhub.core.persistence.AbstractEntity_;
import it.smartcommunitylabdhub.core.persistence.BaseEntity;
import org.springframework.data.jpa.domain.Specification;

public class CommonSpecification {

    public static <T extends BaseEntity> Specification<T> projectEquals(String project) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.equal(root.get(AbstractEntity_.PROJECT), project);
        };
    }

    public static <T extends BaseEntity> Specification<T> keyEquals(String key) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.equal(root.get(AbstractEntity_.KEY), key);
        };
    }

    public static <T extends BaseEntity> Specification<T> createdByEquals(String user) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.equal(root.get(AbstractEntity_.CREATED_BY), user);
        };
    }

    public static <T extends BaseEntity> Specification<T> updatedByEquals(String user) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.equal(root.get(AbstractEntity_.UPDATED_BY), user);
        };
    }

    public static <T extends BaseEntity> Specification<T> nameEquals(String name) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.equal(root.get(Fields.NAME), name);
        };
    }

    public static <T extends BaseEntity> Specification<T> kindEquals(String kind) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.equal(root.get(AbstractEntity_.KIND), kind);
        };
    }

    private CommonSpecification() {}
}
