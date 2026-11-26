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

import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.core.events.EntityEvent;
import it.smartcommunitylabdhub.core.persistence.AbstractEntity;
import it.smartcommunitylabdhub.core.persistence.BaseEntity;
import it.smartcommunitylabdhub.events.EntityAction;
import it.smartcommunitylabdhub.lifecycle.locks.EntityLockException;
import it.smartcommunitylabdhub.lifecycle.locks.EntityLockManager;
import it.smartcommunitylabdhub.lifecycle.locks.EntityLockManager.EntityLock;
import it.smartcommunitylabdhub.lifecycle.locks.LocalEntityLockManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;
import org.springframework.core.convert.converter.Converter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

/**
 * Base repository for entities, backed by JPA.
 *
 * Contract:
 * - every write (create/update/delete) is atomic and runs in its OWN transaction (REQUIRES_NEW),
 *   inside the per-object turn, and publishes its event (delivered after commit)
 * - reads never take the turn; single-object reads are served from an entity cache, and DTOs are
 *   rebuilt on every read, so callers never share instances
 * - after commit, still inside the turn, the cache is updated (write-through) or invalidated
 */
@Slf4j
public abstract class BaseEntityRepositoryImpl<
    E extends BaseEntity,
    D extends BaseDTO
> implements SearchableEntityRepository<E, D>, InitializingBean, ResolvableTypeProvider {

    public static final int PAGE_MAX_SIZE = 1000;
    public static final String CACHE_NAME = "repository.find";

    protected JpaRepository<E, String> repository;

    protected final Class<D> type;
    protected final Class<E> clazz;

    protected Converter<D, E> entityBuilder;
    protected Converter<E, D> dtoBuilder;

    private StringKeyGenerator keyGenerator = () -> UUID.randomUUID().toString().replace("-", "");
    private ApplicationEventPublisher eventPublisher;

    //write transactions: always a new, independent transaction
    private TransactionTemplate transactionTemplate;
    //single object reads on cache miss: new, read only transaction
    private TransactionTemplate readTransactionTemplate;

    private EntityLockManager lockManager = LocalEntityLockManager.shared();

    private CacheManager cacheManager;
    private volatile Cache cache;

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    protected BaseEntityRepositoryImpl() {
        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[1];
        this.type = (Class<D>) t;
        Type t2 = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.clazz = (Class<E>) t2;

        //build generic converters, can be overridden by autowired specific ones
        MapToCborAttributeConverter converter = new MapToCborAttributeConverter();
        this.entityBuilder = new BaseEntityBuilder<>(clazz, converter);
        this.dtoBuilder = new BaseDTOBuilder<>(type, converter);
    }

    @SuppressWarnings("unchecked")
    protected BaseEntityRepositoryImpl(JpaRepository<E, String> repository) {
        Assert.notNull(repository, "repository can not be null");
        this.repository = repository;

        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[1];
        this.type = (Class<D>) t;
        Type t2 = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.clazz = (Class<E>) t2;

        //build generic converters, can be overridden by autowired specific ones
        MapToCborAttributeConverter converter = new MapToCborAttributeConverter();
        this.entityBuilder = new BaseEntityBuilder<>(clazz, converter);
        this.dtoBuilder = new BaseDTOBuilder<>(type, converter);
    }

    @SuppressWarnings("unchecked")
    protected BaseEntityRepositoryImpl(
        JpaRepository<E, String> repository,
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
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[1];
        this.type = (Class<D>) t;
        Type t2 = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.clazz = (Class<E>) t2;
    }

    @Autowired
    public void setRepository(JpaRepository<E, String> repository) {
        this.repository = repository;
    }

    @Autowired(required = false)
    public void setEntityBuilder(Converter<D, E> entityBuilder) {
        this.entityBuilder = entityBuilder;
    }

    @Autowired(required = false)
    public void setDtoBuilder(Converter<E, D> dtoBuilder) {
        this.dtoBuilder = dtoBuilder;
    }

    @Autowired(required = false)
    public void setKeyGenerator(StringKeyGenerator keyGenerator) {
        Assert.notNull(keyGenerator, "key generator can not be null");
        this.keyGenerator = keyGenerator;
    }

    @Autowired
    public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Autowired
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        if (transactionManager == null) {
            throw new IllegalArgumentException("transaction manager can not be null");
        }

        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        this.readTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTransactionTemplate.setReadOnly(true);
    }

    @Autowired(required = false)
    public void setLockManager(EntityLockManager lockManager) {
        if (lockManager != null) {
            this.lockManager = lockManager;
        }
    }

    @Autowired(required = false)
    public void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
        this.cache = null;
    }

    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(repository, "repository can not be null");
        Assert.notNull(entityBuilder, "entity builder can not be null");
        Assert.notNull(dtoBuilder, "dto builder can not be null");
        Assert.notNull(transactionTemplate, "transaction manager can not be null");

        log.debug("Initialized repository for {}", clazz.getSimpleName());
    }

    @Override
    public ResolvableType getResolvableType() {
        return ResolvableType.forClass(clazz);
    }

    @Override
    public Class<D> getType() {
        return type;
    }

    /*
     * Cache
     */
    protected Cache getCache() {
        if (cacheManager == null) {
            return null;
        }

        if (cache == null) {
            //one cache per entity type
            cache = cacheManager.getCache(clazz.getSimpleName() + "." + CACHE_NAME);
        }

        return cache;
    }

    private void cachePut(String id, E entity) {
        Cache c = getCache();
        if (c != null) {
            if (entity != null) {
                c.put(id, entity);
            } else {
                c.evict(id);
            }
        }
    }

    private void cacheEvict(String id) {
        Cache c = getCache();
        if (c != null) {
            c.evict(id);
        }
    }

    private void cacheClear() {
        Cache c = getCache();
        if (c != null) {
            c.clear();
        }
    }

    /*
     * Turns
     */
    private EntityLock acquire(String id) throws StoreException {
        try {
            return lockManager.acquire(type, id);
        } catch (EntityLockException e) {
            throw new StoreException(e.getMessage(), e);
        }
    }

    private void checkNoOuterTransaction(String op) {
        if (log.isDebugEnabled() && TransactionSynchronizationManager.isActualTransactionActive()) {
            //writes are independent: an outer transaction is suspended and holds a second connection
            log.debug("{} on {} invoked within an outer transaction", op, clazz.getSimpleName());
        }
    }

    /*
     * Reads
     */

    /**
     * Load a detached entity, via cache when available.
     * The loader is executed at most once per key, concurrent readers wait for it, and writers
     * updating the same key wait for an in-flight load before overwriting it.
     */
    protected E load(@NotNull String id) {
        Cache c = getCache();
        if (c == null) {
            return loadFromStore(id);
        }

        try {
            return c.get(
                id,
                () -> {
                    E e = loadFromStore(id);
                    if (e == null) {
                        //do not cache misses
                        throw new MissingEntityException();
                    }
                    return e;
                }
            );
        } catch (Cache.ValueRetrievalException e) {
            if (e.getCause() instanceof MissingEntityException) {
                return null;
            }
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }

    private E loadFromStore(String id) {
        return readTransactionTemplate.execute(status ->
            repository
                .findById(id)
                .map(e -> {
                    //detach to keep a stable snapshot, safe to share
                    entityManager.detach(e);
                    return e;
                })
                .orElse(null)
        );
    }

    /*
     * Service
     */
    @Override
    public D create(@NotNull D dto) throws DuplicatedEntityException, StoreException {
        log.debug("create with id {}", String.valueOf(dto.getId()));
        if (log.isTraceEnabled()) {
            log.trace("dto: {}", dto);
        }

        //generate id now if missing
        if (dto.getId() == null) {
            dto.setId(keyGenerator.generateKey());
        }
        String id = dto.getId();

        //build entity
        E entity = entityBuilder.convert(dto);
        if (entity == null) {
            throw new StoreException("failed to convert dto to entity");
        }

        checkNoOuterTransaction("create");

        try (EntityLock lock = acquire(id)) {
            boolean success = false;
            try {
                E saved = transactionTemplate.execute(status -> {
                    //check inside the turn: concurrent creates for the same id are ordered
                    if (repository.existsById(id)) {
                        throw new WrappedException(new DuplicatedEntityException(type, id));
                    }

                    //persist (not merge): an existing row must never be silently updated
                    entityManager.persist(entity);
                    entityManager.flush();

                    //detach to keep all fields (including audit fields) as a stable snapshot
                    entityManager.detach(entity);

                    //publish inside transaction so @TransactionalEventListener fires after commit
                    publish(new EntityEvent<>(entity, EntityAction.CREATE));

                    return entity;
                });

                //committed: write-through
                cachePut(id, saved);
                success = true;

                D res = dtoBuilder.convert(saved);
                if (log.isTraceEnabled()) {
                    log.trace("res: {}", res);
                }

                return res;
            } catch (WrappedException e) {
                if (e.getWrapped() instanceof DuplicatedEntityException de) {
                    throw de;
                }
                throw e.asStoreException();
            } catch (DataIntegrityViolationException | PersistenceException e) {
                if (isUniqueViolation(e)) {
                    throw new DuplicatedEntityException(type, id);
                }
                throw new StoreException("integrity violation on create for " + id + ": " + e.getMessage(), e);
            } finally {
                if (!success) {
                    //extra invalidation on any failure
                    cacheEvict(id);
                }
            }
        }
    }

    @Override
    public D update(@NotNull String id, @NotNull D dto) throws NoSuchEntityException, StoreException {
        log.debug("update with id {}", String.valueOf(id));
        if (log.isTraceEnabled()) {
            log.trace("dto: {}", dto);
        }

        //the dto can not target a different entity
        if (dto.getId() == null) {
            dto.setId(id);
        } else if (!id.equals(dto.getId())) {
            throw new IllegalArgumentException("id mismatch");
        }

        checkNoOuterTransaction("update");

        try (EntityLock lock = acquire(id)) {
            boolean success = false;
            try {
                E updated = transactionTemplate.execute(status -> {
                    E prev = repository
                        .findById(id)
                        .orElseThrow(() -> new WrappedException(new NoSuchEntityException(type)));

                    //detach prev snapshot before overwriting — keeps all fields including audit fields
                    entityManager.detach(prev);

                    //build entity
                    E e = entityBuilder.convert(dto);
                    if (e == null) {
                        throw new WrappedException(new StoreException("failed to convert dto to entity"));
                    }

                    //restore non-modifiable fields, derived entities may freely change any other field
                    if (e instanceof AbstractEntity ae && prev instanceof AbstractEntity prevAe) {
                        ae.setId(prevAe.getId());
                        ae.setKind(prevAe.getKind());
                        ae.setProject(prevAe.getProject());
                        ae.setName(prevAe.getName());

                        ae.setCreated(prevAe.getCreated());
                        ae.setCreatedBy(prevAe.getCreatedBy());
                    }

                    E saved = repository.saveAndFlush(e);

                    //detach to keep all fields (including audit fields) as a stable snapshot
                    entityManager.detach(saved);

                    //publish inside transaction so @TransactionalEventListener fires after commit
                    publish(new EntityEvent<>(saved, prev, EntityAction.UPDATE));

                    return saved;
                });

                //committed: write-through
                cachePut(id, updated);
                success = true;

                D res = dtoBuilder.convert(updated);
                if (log.isTraceEnabled()) {
                    log.trace("res: {}", res);
                }

                return res;
            } catch (WrappedException e) {
                if (e.getWrapped() instanceof NoSuchEntityException ne) {
                    throw ne;
                }
                throw e.asStoreException();
            } catch (DataIntegrityViolationException | PersistenceException e) {
                throw new StoreException("integrity violation on update for " + id + ": " + e.getMessage(), e);
            } finally {
                if (!success) {
                    //extra invalidation on any failure
                    cacheEvict(id);
                }
            }
        }
    }

    @Override
    public void delete(@NotNull String id) throws StoreException {
        log.debug("delete with id {}", id);

        checkNoOuterTransaction("delete");

        try (EntityLock lock = acquire(id)) {
            try {
                transactionTemplate.executeWithoutResult(status ->
                    repository
                        .findById(id)
                        .ifPresent(entity -> {
                            if (log.isTraceEnabled()) {
                                log.trace("entity: {}", entity);
                            }

                            //detach to get a snapshot with all fields (including audit fields)
                            //then delete by id so JPA uses a fresh managed instance
                            entityManager.detach(entity);
                            repository.deleteById(id);

                            //publish inside transaction so @TransactionalEventListener fires after commit
                            publish(new EntityEvent<>(entity, EntityAction.DELETE));
                        })
                );
            } finally {
                //always invalidate, even on failure
                cacheEvict(id);
            }
        }
    }

    @Override
    public D find(@NotNull String id) throws StoreException {
        log.debug("find with id {}", id);

        E e = load(id);
        D res = e != null ? dtoBuilder.convert(e) : null;
        if (log.isTraceEnabled()) {
            log.trace("res: {}", res);
        }

        return res;
    }

    @Override
    public D get(@NotNull String id) throws NoSuchEntityException, StoreException {
        log.debug("get with id {}", id);

        E e = load(id);
        if (e == null) {
            throw new NoSuchEntityException(type);
        }

        D res = dtoBuilder.convert(e);
        if (log.isTraceEnabled()) {
            log.trace("res: {}", res);
        }

        return res;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<D> list(Pageable pageable) {
        log.debug("list with page {}", pageable);

        if (pageable.getPageSize() > PAGE_MAX_SIZE) {
            throw new IllegalArgumentException("max page size exceeded");
        }

        Page<E> page = repository.findAll(pageable);
        List<D> content = page
            .stream()
            .map(e -> dtoBuilder.convert(e))
            .collect(Collectors.toList());

        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public List<D> listAll() {
        log.debug("list all");

        return repository
            .findAll()
            .stream()
            .map(e -> dtoBuilder.convert(e))
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<D> search(Specification<E> specification, Pageable pageable) {
        log.debug("search with spec {} page {}", specification, pageable);

        if (pageable.getPageSize() > PAGE_MAX_SIZE) {
            throw new IllegalArgumentException("max page size exceeded");
        }

        if (repository instanceof JpaSpecificationExecutor) {
            @SuppressWarnings("unchecked")
            Page<E> page = unwrapInvalidSearch(() ->
                ((JpaSpecificationExecutor<E>) repository).findAll(specification, pageable)
            );
            List<D> content = page
                .stream()
                .map(e -> dtoBuilder.convert(e))
                .collect(Collectors.toList());

            return new PageImpl<>(content, pageable, page.getTotalElements());
        }

        throw new UnsupportedOperationException();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<D> search(Example<E> example, Pageable pageable) {
        log.debug("search with example {} page {}", example, pageable);

        if (pageable.getPageSize() > PAGE_MAX_SIZE) {
            throw new IllegalArgumentException("max page size exceeded");
        }

        Page<E> page = repository.findBy(example, query -> query.page(pageable));
        List<D> content = page
            .stream()
            .map(e -> dtoBuilder.convert(e))
            .collect(Collectors.toList());

        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    @SuppressWarnings("unchecked")
    @Override
    @Transactional(readOnly = true)
    public List<D> searchAll(Specification<E> specification) {
        log.debug("search all with spec {} ", specification);

        if (repository instanceof JpaSpecificationExecutor) {
            return unwrapInvalidSearch(() -> ((JpaSpecificationExecutor<E>) repository).findAll(specification))
                .stream()
                .map(e -> dtoBuilder.convert(e))
                .collect(Collectors.toList());
        }

        throw new UnsupportedOperationException();
    }

    @Override
    @Transactional(readOnly = true)
    public List<D> searchAll(Example<E> example) {
        log.debug("search all with example {} ", example);

        return repository
            .findAll(example)
            .stream()
            .map(e -> dtoBuilder.convert(e))
            .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    @Override
    @Transactional(readOnly = true)
    public <T> List<T> searchAll(Specification<E> specification, @NonNull Class<T> projection) {
        log.debug("search all with spec {} ", specification);

        if (repository instanceof JpaSpecificationExecutor) {
            return unwrapInvalidSearch(() ->
                ((JpaSpecificationExecutor<E>) repository).findBy(Specification.allOf(specification), query ->
                        query.as(projection).all()
                    )
            );
        }

        throw new UnsupportedOperationException();
    }

    /*
     * Invalid search criteria raise IllegalArgumentException while building predicates,
     * which the repository proxy translates to InvalidDataAccessApiUsageException:
     * unwrap to surface them as invalid arguments
     */
    private static <T> T unwrapInvalidSearch(Supplier<T> search) {
        try {
            return search.get();
        } catch (InvalidDataAccessApiUsageException e) {
            if (e.getCause() instanceof IllegalArgumentException iae) {
                throw iae;
            }
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public <T> List<T> searchAll(Example<E> example, @NonNull Class<T> projection) {
        log.debug("search all with example {} ", example);

        return repository.findBy(example, query -> query.as(projection).all());
    }

    /*
     * Bulk deletes: a single transaction for the data change, events built from the loaded rows
     */
    @Override
    public long deleteAll() throws StoreException {
        log.debug("delete all");
        return bulkDelete(() -> repository.findAll());
    }

    @SuppressWarnings("unchecked")
    @Override
    public long deleteAll(Specification<E> specification) throws StoreException {
        log.debug("delete all with spec {} ", specification);

        if (repository instanceof JpaSpecificationExecutor) {
            return bulkDelete(() -> ((JpaSpecificationExecutor<E>) repository).findAll(specification));
        }

        throw new UnsupportedOperationException();
    }

    @Override
    public long deleteAll(Example<E> example) throws StoreException {
        log.debug("delete all with example {} ", example);
        return bulkDelete(() -> repository.findAll(example));
    }

    private long bulkDelete(Supplier<List<E>> finder) throws StoreException {
        checkNoOuterTransaction("deleteAll");

        try {
            Integer count = transactionTemplate.execute(status -> {
                //collect all via search
                List<E> entities = finder.get();
                if (entities.isEmpty()) {
                    return 0;
                }

                //detach to keep the loaded rows (with all fields, including audit) as event payload
                entities.forEach(entityManager::detach);
                List<String> ids = entities.stream().map(BaseEntity::getId).toList();

                //remove in batch
                repository.deleteAllByIdInBatch(ids);

                //publish inside transaction so @TransactionalEventListener fires after commit
                entities.forEach(entity -> publish(new EntityEvent<>(entity, EntityAction.DELETE)));

                return entities.size();
            });

            return count != null ? count : 0;
        } catch (DataIntegrityViolationException | PersistenceException e) {
            throw new StoreException("error deleting " + clazz.getSimpleName() + ": " + e.getMessage(), e);
        } finally {
            //always invalidate everything for bulk changes
            cacheClear();
        }
    }

    /*
     * Events
     */
    private void publish(EntityEvent<E> event) {
        if (eventPublisher != null) {
            log.debug("publish event: {} for {}", event.getAction(), event.getEntity().getId());
            if (log.isTraceEnabled()) {
                log.trace("event: {}", String.valueOf(event));
            }

            eventPublisher.publishEvent(event);
        }
    }

    /*
     * Exceptions
     */
    private static boolean isUniqueViolation(Throwable e) {
        //only unique violations are duplicates, everything else is a store error
        Throwable t = e;
        while (t != null) {
            if (t instanceof ConstraintViolationException cve) {
                return cve.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE;
            }
            t = t.getCause();
        }
        return false;
    }

    private static class MissingEntityException extends RuntimeException {

        MissingEntityException() {
            super(null, null, false, false);
        }
    }

    /*
     * Carrier for checked exceptions raised inside transaction callbacks:
     * runtime, so the transaction is rolled back
     */
    private static class WrappedException extends RuntimeException {

        private final Exception wrapped;

        WrappedException(Exception wrapped) {
            super(wrapped.getMessage(), wrapped, false, false);
            this.wrapped = wrapped;
        }

        Exception getWrapped() {
            return wrapped;
        }

        StoreException asStoreException() {
            return wrapped instanceof StoreException se ? se : new StoreException(wrapped.getMessage(), wrapped);
        }
    }
}
