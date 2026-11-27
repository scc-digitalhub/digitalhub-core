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

package it.smartcommunitylabdhub.core.services;

import it.smartcommunitylabdhub.commons.Fields;
import it.smartcommunitylabdhub.commons.accessors.fields.KeyAccessor;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.core.persistence.AbstractEntity_;
import it.smartcommunitylabdhub.core.persistence.BaseEntity;
import it.smartcommunitylabdhub.core.queries.filters.AbstractEntityFilterConverter;
import it.smartcommunitylabdhub.core.queries.specifications.CommonSpecification;
import it.smartcommunitylabdhub.core.repositories.SearchableEntityRepository;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/*
 * Entity service for VERSIONABLE DTOs, where versions are grouped by `project`+`name` and identified by `id`.
 */
@Slf4j
@Transactional
public abstract class BaseVersionableEntityServiceImpl<
    D extends BaseDTO,
    E extends BaseEntity
> implements VersionableEntityService<D>, InitializingBean {

    public static final int PAGE_MAX_SIZE = 1000;
    public static final int DEFAULT_TIMEOUT = 30;

    protected SearchableEntityRepository<E, D> repository;
    protected Converter<D, E> entityBuilder;
    protected Converter<E, D> dtoBuilder;
    protected final Class<D> type;
    protected final Class<E> clazz;

    protected Converter<SearchFilter<D>, SearchFilter<E>> filterConverter;

    protected BaseVersionableEntityServiceImpl() {
        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.type = (Class<D>) t;
        Type t2 = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[1];
        this.clazz = (Class<E>) t2;
    }

    protected BaseVersionableEntityServiceImpl(
        SearchableEntityRepository<E, D> repository,
        Converter<D, E> entityBuilder,
        Converter<E, D> dtoBuilder
    ) {
        Assert.notNull(repository, "repository can not be null");
        Assert.notNull(entityBuilder, "entity builder can not be null");
        Assert.notNull(dtoBuilder, "dto builder can not be null");

        this.repository = repository;
        this.entityBuilder = entityBuilder;
        this.dtoBuilder = dtoBuilder;

        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.type = (Class<D>) t;
        Type t2 = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[1];
        this.clazz = (Class<E>) t2;
    }

    @Autowired
    public void setRepository(SearchableEntityRepository<E, D> repository) {
        this.repository = repository;
    }

    @Autowired
    public void setEntityBuilder(Converter<D, E> entityBuilder) {
        this.entityBuilder = entityBuilder;
    }

    @Autowired
    public void setDtoBuilder(Converter<E, D> dtoBuilder) {
        this.dtoBuilder = dtoBuilder;
    }

    @Autowired(required = false)
    public void setFilterConverter(Converter<SearchFilter<D>, SearchFilter<E>> filterConverter) {
        if (filterConverter != null) {
            this.filterConverter = filterConverter;
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(repository, "repository can not be null");
        Assert.notNull(entityBuilder, "entity builder can not be null");
        Assert.notNull(dtoBuilder, "dto builder can not be null");

        if (filterConverter == null) {
            filterConverter = new AbstractEntityFilterConverter<>(type, clazz) {};
        }
    }

    /*
     * Service
     */
    @Override
    public Class<D> getType() {
        return type;
    }

    @Override
    public List<D> findAll(@NotNull String project, @NotNull String name) throws StoreException {
        log.debug("find all by project {} and name {}", project, name);
        //fetch all versions ordered by date DESC
        Specification<E> where = Specification.allOf(
            CommonSpecification.projectEquals(project),
            CommonSpecification.nameEquals(name)
        );

        Specification<E> spec = (root, query, builder) -> {
            query.orderBy(builder.desc(root.get(AbstractEntity_.CREATED)));
            return where.toPredicate(root, query, builder);
        };

        List<D> list = repository.searchAll(spec);
        log.debug("found {} entities", list.size());

        return list;
    }

    @Override
    public Page<D> findAll(@NotNull String project, @NotNull String name, Pageable pageable) throws StoreException {
        log.debug("find all page {} by project {} and name {}", pageable.getPageNumber(), project, name);
        //fetch all versions ordered by date DESC
        Specification<E> where = Specification.allOf(
            CommonSpecification.projectEquals(project),
            CommonSpecification.nameEquals(name)
        );

        Specification<E> spec = (root, query, builder) -> {
            query.orderBy(builder.desc(root.get(AbstractEntity_.CREATED)));
            return where.toPredicate(root, query, builder);
        };

        Page<D> page = repository.search(spec, pageable);
        log.debug("found {} entities in total", page.getTotalElements());

        return page;
    }

    @Override
    public D getLatestByName(@NotNull String project, @NotNull String name)
        throws NoSuchEntityException, StoreException {
        log.debug("get latest by project {} and name {}", project, name);

        D res = repository
            .searchAll(latestByProjectAndName(project, name))
            .stream()
            .findFirst()
            .orElseThrow(NoSuchEntityException::new);

        if (log.isTraceEnabled()) {
            log.trace("res: {}", res);
        }

        return res;
    }

    @Override
    public D getLatestByKey(@NotNull String project, @NotNull String key) throws NoSuchEntityException, StoreException {
        log.debug("get latest by project {} and key {}", project, key);

        D res = repository
            .searchAll(latestByProjectAndKey(project, key))
            .stream()
            .findFirst()
            .orElseThrow(NoSuchEntityException::new);

        if (log.isTraceEnabled()) {
            log.trace("res: {}", res);
        }

        return res;
    }

    @Override
    public List<D> listLatest() throws StoreException {
        log.debug("list latest");

        List<D> list = repository.searchAll(latest());
        log.debug("found {} entities", list.size());

        return list;
    }

    @Override
    public Page<D> listLatest(Pageable pageable) throws StoreException {
        log.debug("list latest page {} ", pageable.getPageNumber());

        Page<D> page = repository.search(latest(), pageable);
        log.debug("found {} entities in total", page.getTotalElements());

        return page;
    }

    @Override
    public List<D> listLatestByProject(@NotNull String project) throws StoreException {
        log.debug("list latest by project {}", project);

        Specification<E> spec = Specification.allOf(
            CommonSpecification.projectEquals(project),
            latestByProject(project)
        );

        List<D> list = repository.searchAll(spec);
        log.debug("found {} entities", list.size());

        return list;
    }

    @Override
    public Page<D> listLatestByProject(@NotNull String project, Pageable pageable) throws StoreException {
        log.debug("list latest page {} by project {}", pageable.getPageNumber(), project);

        Specification<E> spec = Specification.allOf(
            CommonSpecification.projectEquals(project),
            latestByProject(project)
        );

        Page<D> page = repository.search(spec, pageable);
        log.debug("found {} entities in total", page.getTotalElements());

        return page;
    }

    @Override
    public List<D> searchLatest(SearchFilter<D> filter) throws StoreException {
        log.debug("search latest by filter");
        if (log.isTraceEnabled()) {
            log.trace("filter {}", filter);
        }

        //convert filter
        SearchFilter<E> ef = filter != null ? filterConverter.convert(filter) : null;

        //convert filter to spec
        Specification<E> spec = Specification.allOf(latest(), ef != null ? ef.toSpecification() : null);

        List<D> list = repository.searchAll(spec);
        log.debug("found {} entities", list.size());

        return list;
    }

    @Override
    public Page<D> searchLatest(SearchFilter<D> filter, Pageable pageable) throws StoreException {
        log.debug("search latest page {} by filter", pageable.getPageNumber());
        if (log.isTraceEnabled()) {
            log.trace("filter {}", filter);
        }

        //convert filter
        SearchFilter<E> ef = filter != null ? filterConverter.convert(filter) : null;

        //convert filter to spec
        Specification<E> spec = Specification.allOf(latest(), ef != null ? ef.toSpecification() : null);

        Page<D> page = repository.search(spec, pageable);
        log.debug("found {} entities in total", page.getTotalElements());

        return page;
    }

    @Override
    public List<D> searchLatestByProject(@NotNull String project, SearchFilter<D> filter) throws StoreException {
        log.debug("search latest by filter for project {}", project);
        if (log.isTraceEnabled()) {
            log.trace("filter {}", filter);
        }

        //convert filter
        SearchFilter<E> ef = filter != null ? filterConverter.convert(filter) : null;

        //convert filter to spec
        Specification<E> spec = Specification.allOf(
            CommonSpecification.projectEquals(project),
            latestByProject(project),
            ef != null ? ef.toSpecification() : null
        );

        List<D> list = repository.searchAll(spec);
        log.debug("found {} entities", list.size());

        return list;
    }

    @Override
    public Page<D> searchLatestByProject(@NotNull String project, SearchFilter<D> filter, Pageable pageable)
        throws StoreException {
        log.debug("search latest page {} by filter for project {}", pageable.getPageNumber(), project);
        if (log.isTraceEnabled()) {
            log.trace("filter {}", filter);
        }

        //convert filter
        SearchFilter<E> ef = filter != null ? filterConverter.convert(filter) : null;

        //convert filter to spec
        Specification<E> spec = Specification.allOf(
            CommonSpecification.projectEquals(project),
            latestByProject(project),
            ef != null ? ef.toSpecification() : null
        );

        Page<D> page = repository.search(spec, pageable);
        log.debug("found {} entities in total", page.getTotalElements());

        return page;
    }

    /*
     * Queries
     */
    public <T extends BaseEntity> Specification<T> latest() {
        return (root, query, criteriaBuilder) -> {
            Subquery<Number> subquery = query.subquery(Number.class);
            Root<T> subqueryRoot = (Root<T>) subquery.from(root.getJavaType());

            subquery.select(criteriaBuilder.max(subqueryRoot.get(AbstractEntity_.CREATED)));
            subquery.where(criteriaBuilder.equal(subqueryRoot.get(Fields.NAME), root.get(Fields.NAME)));
            subquery.groupBy(subqueryRoot.get(Fields.NAME), subqueryRoot.get(AbstractEntity_.PROJECT));

            return criteriaBuilder.and(criteriaBuilder.in(root.get(AbstractEntity_.CREATED)).value(subquery));
        };
    }

    public <T extends BaseEntity> Specification<T> latestByProject(String project) {
        return (root, query, criteriaBuilder) -> {
            Subquery<Number> subquery = query.subquery(Number.class);
            Root<T> subqueryRoot = (Root<T>) subquery.from(root.getJavaType());

            subquery.select(criteriaBuilder.max(subqueryRoot.get(AbstractEntity_.CREATED)));
            subquery.where(
                criteriaBuilder.equal(subqueryRoot.get(AbstractEntity_.PROJECT), project),
                criteriaBuilder.equal(subqueryRoot.get(Fields.NAME), root.get(Fields.NAME))
            );
            subquery.groupBy(subqueryRoot.get(Fields.NAME), subqueryRoot.get(AbstractEntity_.PROJECT));

            return criteriaBuilder.and(
                criteriaBuilder.equal(root.get(AbstractEntity_.PROJECT), project),
                criteriaBuilder.in(root.get(AbstractEntity_.CREATED)).value(subquery)
            );
        };
    }

    public <T extends BaseEntity> Specification<T> latestByProjectAndName(String project, String name) {
        return (root, query, criteriaBuilder) -> {
            Subquery<Number> subquery = query.subquery(Number.class);
            Root<T> subqueryRoot = (Root<T>) subquery.from(root.getJavaType());

            subquery.select(criteriaBuilder.max(subqueryRoot.get(AbstractEntity_.CREATED)));
            subquery.where(
                criteriaBuilder.equal(subqueryRoot.get(AbstractEntity_.PROJECT), project),
                criteriaBuilder.equal(subqueryRoot.get(Fields.NAME), name)
            );

            return criteriaBuilder.and(
                criteriaBuilder.equal(root.get(AbstractEntity_.PROJECT), project),
                criteriaBuilder.equal(root.get(Fields.NAME), name),
                criteriaBuilder.in(root.get(AbstractEntity_.CREATED)).value(subquery)
            );
        };
    }

    public <T extends BaseEntity> Specification<T> latestByProjectAndKey(String project, String key) {
        return (root, query, criteriaBuilder) -> {
            Subquery<Number> subquery = query.subquery(Number.class);
            Root<T> subqueryRoot = (Root<T>) subquery.from(root.getJavaType());

            KeyAccessor accessor = KeyAccessor.with(key);

            //validate key
            if (!project.equals(accessor.getProject())) {
                throw new IllegalArgumentException("Project in key does not match the provided project");
            }

            if (!this.type.getName().toLowerCase().equals(accessor.getType())) {
                throw new IllegalArgumentException("Type in key does not match the expected type");
            }

            if (StringUtils.hasText(accessor.getId())) {
                //exact match with id
                return criteriaBuilder.and(
                    criteriaBuilder.equal(root.get(AbstractEntity_.PROJECT), project),
                    criteriaBuilder.equal(root.get(AbstractEntity_.ID), accessor.getId())
                );
            } else {
                //like match on key without id
                //exact match with id
                subquery.select(criteriaBuilder.max(subqueryRoot.get(AbstractEntity_.CREATED)));
                subquery.where(
                    criteriaBuilder.equal(subqueryRoot.get(AbstractEntity_.PROJECT), project),
                    criteriaBuilder.like(subqueryRoot.get(AbstractEntity_.KEY), key + "%")
                );

                return criteriaBuilder.and(
                    criteriaBuilder.equal(root.get(AbstractEntity_.PROJECT), project),
                    criteriaBuilder.like(root.get(AbstractEntity_.KEY), key + "%"),
                    criteriaBuilder.in(root.get(AbstractEntity_.CREATED)).value(subquery)
                );
            }
        };
    }
}
