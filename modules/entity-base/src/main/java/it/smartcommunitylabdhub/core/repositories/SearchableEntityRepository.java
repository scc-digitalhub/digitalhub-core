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

package it.smartcommunitylabdhub.core.repositories;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.core.persistence.BaseEntity;
import java.util.List;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;

public interface SearchableEntityRepository<E extends BaseEntity, D extends BaseDTO> extends EntityRepository<D> {
    List<D> searchAll(Specification<E> specification) throws StoreException;
    List<D> searchAll(Example<E> example) throws StoreException;

    <T> List<T> searchAll(Specification<E> specification, @NonNull Class<T> projection) throws StoreException;
    <T> List<T> searchAll(Example<E> example, @NonNull Class<T> projection) throws StoreException;

    Page<D> search(Specification<E> specification, Pageable page) throws StoreException;
    Page<D> search(Example<E> example, Pageable page) throws StoreException;

    long deleteAll(Specification<E> specification) throws StoreException;
    long deleteAll(Example<E> example) throws StoreException;
}
