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
import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.metadata.MetadataDTO;
import it.smartcommunitylabdhub.commons.models.metadata.VersioningMetadata;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.commons.services.SpecRegistry;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.core.components.infrastructure.specs.SpecValidator;
import it.smartcommunitylabdhub.core.persistence.BaseEntity;
import it.smartcommunitylabdhub.core.queries.filters.AbstractEntityFilterConverter;
import it.smartcommunitylabdhub.core.queries.specifications.CommonSpecification;
import it.smartcommunitylabdhub.core.repositories.SearchableEntityRepository;
import it.smartcommunitylabdhub.core.utils.NamesGenerator;
import it.smartcommunitylabdhub.lifecycle.LifecycleManager;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
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
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;

/*
 * Entity service for DTOs with SPEC + STATUS, optional lifecycle management included.
 * If tools available, specs are parsed and validated.
 * Supports search via filters.
 */
@Slf4j
@Transactional
public abstract class BaseEntityServiceImpl<D extends BaseDTO & SpecDTO & StatusDTO, E extends BaseEntity>
    implements EntityService<D>, InitializingBean {

    public static final int PAGE_MAX_SIZE = 1000;
    public static final int DEFAULT_TIMEOUT = 30;
    protected final EntityName type;

    protected SearchableEntityRepository<E, D> repository;
    protected Converter<D, E> entityBuilder;
    protected Converter<E, D> dtoBuilder;

    protected EntityFinalizer<D> finalizer;
    protected SpecRegistry specRegistry;
    protected SpecValidator validator;
    protected LifecycleManager<D> lifecycleManager;
    protected Converter<SearchFilter<D>, SearchFilter<E>> filterConverter = new AbstractEntityFilterConverter<>();

    protected NamesGenerator nameGenerator;

    @SuppressWarnings("unchecked")
    protected BaseEntityServiceImpl() {
        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.type = EntityUtils.getEntityName((Class<D>) t);
    }

    @SuppressWarnings("unchecked")
    protected BaseEntityServiceImpl(
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
        this.type = EntityUtils.getEntityName((Class<D>) t);
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
    public void setSpecRegistry(SpecRegistry specRegistry) {
        this.specRegistry = specRegistry;
    }

    @Autowired(required = false)
    public void setValidator(SpecValidator validator) {
        this.validator = validator;
    }

    @Autowired(required = false)
    public void setLifecycleManager(LifecycleManager<D> lifecycleManager) {
        this.lifecycleManager = lifecycleManager;
    }

    @Autowired(required = false)
    public void setFilterConverter(Converter<SearchFilter<D>, SearchFilter<E>> filterConverter) {
        if (filterConverter != null) {
            this.filterConverter = filterConverter;
        }
    }

    @Autowired(required = false)
    public void setNameGenerator(NamesGenerator nameGenerator) {
        this.nameGenerator = nameGenerator;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(repository, "repository can not be null");
        Assert.notNull(entityBuilder, "entity builder can not be null");
        Assert.notNull(dtoBuilder, "dto builder can not be null");
        Assert.notNull(filterConverter, "filter converter can not be null");
    }

    @Override
    public String toString() {
        return String.format("%s[type=%s]", this.getClass().getSimpleName(), type);
    }

    protected EntityFinalizer<D> getFinalizer() {
        return finalizer;
    }

    protected SpecRegistry getSpecRegistry() {
        return specRegistry;
    }

    protected SpecValidator getValidator() {
        return validator;
    }

    protected LifecycleManager<D> getLifecycleManager() {
        return lifecycleManager;
    }

    protected Converter<SearchFilter<D>, SearchFilter<E>> getFilterConverter() {
        return filterConverter;
    }

    /*
     * Service
     */
    @Override
    public EntityName getType() {
        return type;
    }

    @Override
    public D create(@NotNull D dto)
        throws IllegalArgumentException, BindException, DuplicatedEntityException, StoreException {
        log.debug("create with id {}", String.valueOf(dto.getId()));
        if (log.isTraceEnabled()) {
            log.trace("dto: {}", dto);
        }

        //validate project
        String projectId = dto.getProject();
        if (!StringUtils.hasText(projectId)) {
            throw new IllegalArgumentException("invalid or missing project");
        }

        if (getSpecRegistry() != null) {
            // Parse and export Spec
            Spec spec = getSpecRegistry().createSpec(dto.getKind(), dto.getSpec());
            if (spec == null) {
                throw new IllegalArgumentException("invalid kind");
            }

            if (getValidator() != null) {
                //validate spec
                getValidator().validateSpec(spec);
            }

            //update spec as exported
            dto.setSpec(spec.toMap());
        }

        //check version name in metadata if supported
        if (dto instanceof MetadataDTO && nameGenerator != null) {
            MetadataDTO metaDto = (MetadataDTO) dto;
            Map<String, Serializable> meta = metaDto.getMetadata() != null ? metaDto.getMetadata() : Map.of();
            VersioningMetadata versioning = VersioningMetadata.from(meta);

            if (
                !StringUtils.hasText(versioning.getVersion()) ||
                (dto.getId() != null && dto.getId().equals(versioning.getVersion()))
            ) {
                String version = nameGenerator.generateKey();
                log.trace("autogenerated version name {}", version);
                versioning.setVersion(version);
                metaDto.setMetadata(MapUtils.mergeMultipleMaps(meta, versioning.toMap()));
            }
        }

        //on create status is *always* CREATED
        //keep the user provided and move via lifecycle if needed
        StatusFieldAccessor status = StatusFieldAccessor.with(dto.getStatus());
        String curState = "CREATED";
        String nextState = status.getState() == null ? "CREATED" : status.getState();

        if (getLifecycleManager() == null) {
            //no lifecycle action, use next state
            curState = nextState;
        }

        dto.setStatus(MapUtils.mergeMultipleMaps(dto.getStatus(), Map.of(Fields.STATE, curState)));

        //save
        if (log.isTraceEnabled()) {
            log.trace("dto: {}", dto);
        }

        //persist
        D res = repository.create(dto);
        if (log.isTraceEnabled()) {
            log.trace("res: {}", res);
        }

        if (getLifecycleManager() != null && !nextState.equals(curState)) {
            //perform transition
            res = getLifecycleManager().handle(dto, nextState);
        }

        return res;
    }

    @Override
    public D update(@NotNull String id, @NotNull D dto) throws NoSuchEntityException, StoreException {
        return update(id, dto, false);
    }

    @Override
    public D update(@NotNull String id, @NotNull D dto, boolean forceUpdate)
        throws NoSuchEntityException, StoreException {
        log.debug("update with id {}", String.valueOf(dto.getId()));
        if (log.isTraceEnabled()) {
            log.trace("dto: {}", dto);
        }
        //fetch current and merge
        D current = repository.get(id);
        if (current == null) {
            throw new StoreException("Invalid or broken entity in store");
        }

        //we assume that missing status means CREATED
        StatusFieldAccessor curStatus = StatusFieldAccessor.with(current.getStatus());
        String curState = curStatus.getState() == null ? "CREATED" : curStatus.getState();

        StatusFieldAccessor nextStatus = StatusFieldAccessor.with(dto.getStatus());
        String nextState = nextStatus.getState() == null ? "CREATED" : nextStatus.getState();

        if (getLifecycleManager() == null) {
            //no lifecycle action, use next state
            curState = nextState;
        }

        //keep current state for update, we evaluate later
        dto.setStatus(MapUtils.mergeMultipleMaps(dto.getStatus(), Map.of(Fields.STATE, curState)));

        if (!forceUpdate) {
            //spec is not modifiable: enforce current
            dto.setSpec(current.getSpec());
        }

        if (!curState.equals(nextState) && getLifecycleManager() != null) {
            //move to next state
            log.debug("state change update from {} to {}, handle via lifecycle", curState, nextState);

            //update via lifecycle transition
            D res = getLifecycleManager().handle(dto, nextState);
            if (log.isTraceEnabled()) {
                log.trace("res: {}", res);
            }

            return res;
        } else {
            //keep same state
            log.debug("same state update {}, handle via store", curState);

            //direct update
            if (log.isTraceEnabled()) {
                log.trace("dto: {}", dto);
            }

            //persist
            D res = repository.update(id, dto);
            if (log.isTraceEnabled()) {
                log.trace("res: {}", res);
            }

            return res;
        }
    }

    @Override
    public void delete(@NotNull String id, @Nullable Boolean cascade) throws StoreException {
        log.debug("delete with id {}", id);

        D e = repository.find(id);
        if (e != null) {
            //we assume that missing status means CREATED
            StatusFieldAccessor curStatus = StatusFieldAccessor.with(e.getStatus());
            String curState = curStatus.getState() == null ? "CREATED" : curStatus.getState();

            if (getLifecycleManager() != null && !"DELETED".equals(curState)) {
                //delegate to lifecycle manager, will perform cascade effect on success
                log.debug("handle delete via lifecycle manager for {}", id);
                lifecycleManager.perform(
                    e,
                    "DELETE",
                    cascade,
                    (dto, r) -> {
                        try {
                            if (Boolean.TRUE.equals(cascade) && getFinalizer() != null) {
                                //perform gc
                                getFinalizer().finalize(dto);
                            }
                            //entity delete is delegated to lm
                        } catch (StoreException e1) {
                            log.error("error deleting side effect: {}", e1.getMessage());
                        }
                    }
                );
            } else {
                //no lifecycle manager or already completed, just delete
                log.debug("no lifecycle manager, delete directly");

                if (Boolean.TRUE.equals(cascade) && getFinalizer() != null) {
                    //perform gc
                    getFinalizer().finalize(e);
                }

                //entity delete
                repository.delete(id);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public D find(@NotNull String id) throws StoreException {
        log.debug("find with id {}", id);

        D res = repository.find(id);
        if (log.isTraceEnabled()) {
            log.trace("res: {}", res);
        }

        if (res == null) {
            return null;
        }

        return res;
    }

    @Override
    @Transactional(readOnly = true)
    public D get(@NotNull String id) throws NoSuchEntityException, StoreException {
        log.debug("get with id {}", id);

        D res = repository.get(id);
        if (log.isTraceEnabled()) {
            log.trace("res: {}", res);
        }

        return res;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<D> list(Pageable pageable) throws StoreException {
        log.debug("list with page {}", pageable);

        if (pageable.getPageSize() > PAGE_MAX_SIZE) {
            throw new IllegalArgumentException("max page size exceeded");
        }

        return repository.list(pageable);
    }

    @Override
    public List<D> listAll() throws StoreException {
        log.debug("list all");

        return repository.listAll();
    }

    @Override
    public void deleteAll(@Nullable Boolean cascade) throws StoreException {
        log.debug("delete all");
        if (Boolean.TRUE.equals(cascade)) {
            //delete one by one with cascade
            repository
                .listAll()
                .forEach(e -> {
                    try {
                        delete(e.getId(), cascade);
                    } catch (StoreException ex) {
                        log.error("Error deleting {}: {}", e.getId(), ex.getMessage());
                    }
                });
        } else {
            //bulk delete
            long count = repository.deleteAll();
            log.debug("deleted {} entities", count);
        }
    }

    @Override
    public void deleteByUser(@NotNull String user, @Nullable Boolean cascade) throws StoreException {
        log.debug("delete all by user {}", user);
        if (Boolean.TRUE.equals(cascade)) {
            //delete one by one with cascade
            repository
                .searchAll(CommonSpecification.createdByEquals(user))
                .forEach(e -> {
                    try {
                        delete(e.getId(), cascade);
                    } catch (StoreException ex) {
                        log.error("Error deleting {}: {}", e.getId(), ex.getMessage());
                    }
                });
        } else {
            //bulk delete
            long count = repository.deleteAll(CommonSpecification.createdByEquals(user));
            log.debug("deleted {} entities", count);
        }
    }

    @Override
    public void deleteByProject(@NotNull String project, @Nullable Boolean cascade) throws StoreException {
        log.debug("delete all by project {}", project);
        if (Boolean.TRUE.equals(cascade)) {
            //delete one by one with cascade
            repository
                .searchAll(CommonSpecification.projectEquals(project))
                .forEach(e -> {
                    try {
                        delete(e.getId(), cascade);
                    } catch (StoreException ex) {
                        log.error("Error deleting {}: {}", e.getId(), ex.getMessage());
                    }
                });
        } else {
            //bulk delete
            long count = repository.deleteAll(CommonSpecification.projectEquals(project));
            log.debug("deleted {} entities", count);
        }
    }

    @Override
    public void deleteByKind(@NotNull String kind, @Nullable Boolean cascade) throws StoreException {
        log.debug("delete all by kind {}", kind);
        if (Boolean.TRUE.equals(cascade)) {
            //delete one by one with cascade
            repository
                .searchAll(CommonSpecification.kindEquals(kind))
                .forEach(e -> {
                    try {
                        delete(e.getId(), cascade);
                    } catch (StoreException ex) {
                        log.error("Error deleting {}: {}", e.getId(), ex.getMessage());
                    }
                });
        } else {
            //bulk delete
            long count = repository.deleteAll(CommonSpecification.kindEquals(kind));
            log.debug("deleted {} entities", count);
        }
    }

    @Override
    public List<D> listByUser(@NotNull String user) throws StoreException {
        log.debug("list all by user {}", user);

        List<D> list = repository.searchAll(CommonSpecification.createdByEquals(user));
        log.debug("found {} entities", list.size());

        return list;
    }

    @Override
    public Page<D> listByUser(@NotNull String user, Pageable pageable) throws StoreException {
        log.debug("list page {} by user {}", pageable.getPageNumber(), user);

        Page<D> page = repository.search(CommonSpecification.createdByEquals(user), pageable);
        log.debug("found {} entities in total", page.getTotalElements());

        return page;
    }

    @Override
    public List<D> listByProject(@NotNull String project) throws StoreException {
        log.debug("list all by project {}", project);

        List<D> list = repository.searchAll(CommonSpecification.projectEquals(project));
        log.debug("found {} entities", list.size());

        return list;
    }

    @Override
    public Page<D> listByProject(@NotNull String project, Pageable pageable) throws StoreException {
        log.debug("list page {} by project {}", pageable.getPageNumber(), project);

        Page<D> page = repository.search(CommonSpecification.projectEquals(project), pageable);
        log.debug("found {} entities in total", page.getTotalElements());

        return page;
    }

    @Override
    public List<D> listByKind(@NotNull String kind) throws StoreException {
        log.debug("list all by kind {}", kind);

        List<D> list = repository.searchAll(CommonSpecification.kindEquals(kind));
        log.debug("found {} entities", list.size());

        return list;
    }

    @Override
    public Page<D> listByKind(@NotNull String kind, Pageable pageable) throws StoreException {
        log.debug("list page {} by kind {}", pageable.getPageNumber(), kind);

        Page<D> page = repository.search(CommonSpecification.kindEquals(kind), pageable);
        log.debug("found {} entities in total", page.getTotalElements());

        return page;
    }

    @Override
    public List<D> search(SearchFilter<D> filter) throws StoreException {
        log.debug("search all by filter");
        if (log.isTraceEnabled()) {
            log.trace("filter {}", filter);
        }

        //convert filter
        SearchFilter<E> ef = filter != null ? getFilterConverter().convert(filter) : null;

        List<D> list = ef != null ? repository.searchAll(ef.toSpecification()) : repository.listAll();
        log.debug("found {} entities", list.size());

        return list;
    }

    @Override
    public Page<D> search(SearchFilter<D> filter, Pageable pageable) throws StoreException {
        log.debug("search all page {} by filter", pageable.getPageNumber());
        if (log.isTraceEnabled()) {
            log.trace("filter {}", filter);
        }

        //convert filter
        SearchFilter<E> ef = filter != null ? getFilterConverter().convert(filter) : null;

        Page<D> page = ef != null ? repository.search(ef.toSpecification(), pageable) : repository.list(pageable);
        log.debug("found {} entities in total", page.getTotalElements());

        return page;
    }

    @Override
    public List<D> searchByProject(@NotNull String project, SearchFilter<D> filter) throws StoreException {
        log.debug("search all by filter for project {}", project);
        if (log.isTraceEnabled()) {
            log.trace("filter {}", filter);
        }

        //convert filter
        SearchFilter<E> ef = filter != null ? getFilterConverter().convert(filter) : null;

        //convert filter to spec
        Specification<E> spec = Specification.allOf(
            CommonSpecification.projectEquals(project),
            ef != null ? ef.toSpecification() : null
        );

        List<D> list = repository.searchAll(spec);
        log.debug("found {} entities", list.size());

        return list;
    }

    @Override
    public Page<D> searchByProject(@NotNull String project, SearchFilter<D> filter, Pageable pageable)
        throws StoreException {
        log.debug("search all page {} by filter for project {}", pageable.getPageNumber(), project);
        if (log.isTraceEnabled()) {
            log.trace("filter {}", filter);
        }

        //convert filter
        SearchFilter<E> ef = filter != null ? getFilterConverter().convert(filter) : null;

        //convert filter to spec
        Specification<E> spec = Specification.allOf(
            CommonSpecification.projectEquals(project),
            ef != null ? ef.toSpecification() : null
        );

        Page<D> page = repository.search(spec, pageable);
        log.debug("found {} entities in total", page.getTotalElements());

        return page;
    }
}
