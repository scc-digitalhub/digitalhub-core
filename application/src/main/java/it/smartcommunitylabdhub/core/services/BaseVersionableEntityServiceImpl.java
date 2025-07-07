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

import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.services.VersionableEntityService;
import it.smartcommunitylabdhub.core.persistence.AbstractEntity_;
import it.smartcommunitylabdhub.core.persistence.BaseEntity;
import it.smartcommunitylabdhub.core.queries.filters.AbstractEntityFilterConverter;
import it.smartcommunitylabdhub.core.queries.specifications.CommonSpecification;
import it.smartcommunitylabdhub.core.repositories.SearchableEntityRepository;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/*
 * Entity service for VERSIONABLE DTOs, where versions are grouped by `project`+`name` and identified by `id`.
 */
@Slf4j
@Transactional
public class BaseVersionableEntityServiceImpl<D extends BaseDTO, E extends BaseEntity>
    implements VersionableEntityService<D>, InitializingBean {

    public static final int PAGE_MAX_SIZE = 1000;
    public static final int DEFAULT_TIMEOUT = 30;

    protected SearchableEntityRepository<E, D> repository;
    protected Converter<D, E> entityBuilder;
    protected Converter<E, D> dtoBuilder;
    protected EntityFinalizer<D> finalizer;

    protected Converter<SearchFilter<D>, SearchFilter<E>> filterConverter = new AbstractEntityFilterConverter<>();

    protected BaseVersionableEntityServiceImpl() {}

    public BaseVersionableEntityServiceImpl(
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
    public void setFinalizer(EntityFinalizer<D> finalizer) {
        this.finalizer = finalizer;
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
        Assert.notNull(filterConverter, "filter converter can not be null");
    }

    /*
     * Service
     */

    @Override
    public void deleteAll(@NotNull String project, @NotNull String name, @Nullable Boolean cascade)
        throws StoreException {
        log.debug("delete all by project {} and name {}", project, name);
        Specification<E> spec = Specification.allOf(
            CommonSpecification.projectEquals(project),
            CommonSpecification.nameEquals(name)
        );

        if (Boolean.TRUE.equals(cascade)) {
            if (finalizer == null) {
                //no cascade available!
                log.warn("Cascade delete not supported");
                throw new IllegalArgumentException();
            }

            //delete one by one with cascade
            repository
                .searchAll(spec)
                .forEach(e -> {
                    try {
                        //first perform gc
                        finalizer.finalize(e);

                        //then remove from repo
                        repository.delete(e.getId());
                    } catch (StoreException ex) {
                        log.error("Error deleting {}: {}", e.getId(), ex.getMessage());
                    }
                });
        } else {
            //bulk delete
            long count = repository.deleteAll(spec);
            log.debug("deleted {} entities", count);
        }
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
    public D getLatest(@NotNull String project, @NotNull String name) throws NoSuchEntityException, StoreException {
        log.debug("get latest by project {} and name {}", project, name);

        D res = repository
            .searchAll(CommonSpecification.latestByProject(project, name))
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

        List<D> list = repository.searchAll(CommonSpecification.latest());
        log.debug("found {} entities", list.size());

        return list;
    }

    @Override
    public Page<D> listLatest(Pageable pageable) throws StoreException {
        log.debug("list latest page {} ", pageable.getPageNumber());

        Page<D> page = repository.search(CommonSpecification.latest(), pageable);
        log.debug("found {} entities in total", page.getTotalElements());

        return page;
    }

    @Override
    public List<D> listLatestByProject(@NotNull String project) throws StoreException {
        log.debug("list latest by project {}", project);

        Specification<E> spec = Specification.allOf(
            CommonSpecification.projectEquals(project),
            CommonSpecification.latestByProject(project)
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
            CommonSpecification.latestByProject(project)
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
        Specification<E> spec = Specification.allOf(
            CommonSpecification.latest(),
            ef != null ? ef.toSpecification() : null
        );

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
        Specification<E> spec = Specification.allOf(
            CommonSpecification.latest(),
            ef != null ? ef.toSpecification() : null
        );

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
            CommonSpecification.latestByProject(project),
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
            CommonSpecification.latestByProject(project),
            ef != null ? ef.toSpecification() : null
        );

        Page<D> page = repository.search(spec, pageable);
        log.debug("found {} entities in total", page.getTotalElements());

        return page;
    }
}
