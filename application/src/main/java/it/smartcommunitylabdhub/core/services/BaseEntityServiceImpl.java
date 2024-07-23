package it.smartcommunitylabdhub.core.services;

import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.core.models.base.BaseEntity;
import it.smartcommunitylabdhub.core.models.entities.AbstractEntity;
import it.smartcommunitylabdhub.core.models.events.EntityAction;
import it.smartcommunitylabdhub.core.models.events.EntityEvent;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.util.Pair;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Slf4j
@Transactional
public class BaseEntityServiceImpl<D extends BaseDTO, E extends BaseEntity> implements EntityService<D, E> {

    public static final int PAGE_MAX_SIZE = 1000;
    public static final int DEFAULT_TIMEOUT = 30;
    protected final JpaRepository<E, String> repository;

    protected final Converter<D, E> entityBuilder;
    protected final Converter<E, D> dtoBuilder;

    private ApplicationEventPublisher eventPublisher;

    private Map<String, Pair<ReentrantLock, Instant>> locks = new ConcurrentHashMap<>();
    private int timeout = DEFAULT_TIMEOUT;

    public BaseEntityServiceImpl(
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
    }

    @Autowired
    public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /*
     * Locking w/cache
     */
    public void setTimeout(int t) {
        this.timeout = t;
    }

    private synchronized ReentrantLock getLock(String id) {
        //build lock
        ReentrantLock l = locks.containsKey(id) ? locks.get(id).getFirst() : new ReentrantLock();

        //update last used date
        locks.put(id, Pair.of(l, Instant.now()));

        return l;
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

        //check for existing ids
        if (dto.getId() != null && (repository.existsById(dto.getId()))) {
            throw new DuplicatedEntityException(dto.getId());
        }

        //generate id now
        String id = dto.getId() != null ? dto.getId() : UUID.randomUUID().toString();
        dto.setId(id);

        try {
            //acquire write lock
            getLock(id).tryLock(timeout, TimeUnit.SECONDS);

            try {
                //build entity
                E entity = entityBuilder.convert(dto);

                //persist
                entity = repository.saveAndFlush(entity);
                if (log.isTraceEnabled()) {
                    log.trace("entity: {}", entity);
                }

                //publish
                if (eventPublisher != null) {
                    log.debug("publish event: create for {}", entity.getId());
                    EntityEvent<E> event = new EntityEvent<>(entity, EntityAction.CREATE);
                    if (log.isTraceEnabled()) {
                        log.trace("event: {}", String.valueOf(event));
                    }

                    eventPublisher.publishEvent(event);
                }

                D res = dtoBuilder.convert(entity);
                if (log.isTraceEnabled()) {
                    log.trace("res: {}", res);
                }

                return res;
            } finally {
                getLock(id).unlock();
            }
        } catch (InterruptedException e) {
            throw new StoreException("unable to access the store: " + e.getMessage());
        }
    }

    @Override
    public D update(@NotNull String id, @NotNull D dto) throws NoSuchEntityException, StoreException {
        log.debug("update with id {}", String.valueOf(dto.getId()));
        if (log.isTraceEnabled()) {
            log.trace("dto: {}", dto);
        }

        try {
            //acquire write lock
            getLock(id).tryLock(timeout, TimeUnit.SECONDS);

            try {
                E entity = repository.findById(id).orElseThrow(() -> new NoSuchEntityException());

                //build entity
                E e = entityBuilder.convert(dto);

                if (e instanceof AbstractEntity) {
                    AbstractEntity ae = (AbstractEntity) e;
                    //enforce non-modifiable fields
                    ae.setId(entity.getId());
                    ae.setKind(entity.getKind());
                    ae.setProject(entity.getProject());

                    ae.setCreated(entity.getCreated());
                    ae.setCreatedBy(entity.getCreatedBy());
                }

                D prevAsDto = dtoBuilder.convert(entity);

                //persist
                E updated = repository.saveAndFlush(e);
                if (log.isTraceEnabled()) {
                    log.trace("entity: {}", updated);
                }

                //publish
                if (eventPublisher != null) {
                    log.debug("publish event: update for {}", id);
                    EntityEvent<E> event = new EntityEvent<>(updated, entityBuilder.convert(prevAsDto), EntityAction.UPDATE);
                    if (log.isTraceEnabled()) {
                        log.trace("event: {}", String.valueOf(event));
                    }

                    eventPublisher.publishEvent(event);
                }

                D res = dtoBuilder.convert(updated);
                if (log.isTraceEnabled()) {
                    log.trace("res: {}", res);
                }

                return res;
            } finally {
                getLock(id).unlock();
            }
        } catch (InterruptedException e) {
            throw new StoreException("unable to access the store: " + e.getMessage());
        }
    }

    @Override
    public void delete(@NotNull String id) throws StoreException {
        log.debug("delete with id {}", id);
        try {
            //acquire write lock
            getLock(id).tryLock(timeout, TimeUnit.SECONDS);

            try {
                repository
                    .findById(id)
                    .ifPresent(entity -> {
                        if (log.isTraceEnabled()) {
                            log.trace("entity: {}", entity);
                        }

                        repository.delete(entity);

                        //publish
                        if (eventPublisher != null) {
                            log.debug("publish event: delete for {}", id);
                            EntityEvent<E> event = new EntityEvent<>(entity, EntityAction.DELETE);
                            if (log.isTraceEnabled()) {
                                log.trace("event: {}", String.valueOf(event));
                            }

                            eventPublisher.publishEvent(event);
                        }
                    });
            } finally {
                getLock(id).unlock();
            }
        } catch (InterruptedException e) {
            throw new StoreException("unable to access the store: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public D find(@NotNull String id) throws StoreException {
        log.debug("find with id {}", id);

        try {
            //acquire write lock
            getLock(id).tryLock(timeout, TimeUnit.SECONDS);

            try {
                D res = repository.findById(id).map(e -> dtoBuilder.convert(e)).orElse(null);
                if (log.isTraceEnabled()) {
                    log.trace("res: {}", res);
                }

                return res;
            } finally {
                getLock(id).unlock();
            }
        } catch (InterruptedException e) {
            throw new StoreException("unable to access the store: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public D get(@NotNull String id) throws NoSuchEntityException, StoreException {
        log.debug("get with id {}", id);
        try {
            //acquire write lock
            getLock(id).tryLock(timeout, TimeUnit.SECONDS);

            try {
                D res = repository
                    .findById(id)
                    .map(e -> dtoBuilder.convert(e))
                    .orElseThrow(() -> new NoSuchEntityException());
                if (log.isTraceEnabled()) {
                    log.trace("res: {}", res);
                }

                return res;
            } finally {
                getLock(id).unlock();
            }
        } catch (InterruptedException e) {
            throw new StoreException("unable to access the store: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<D> list(Pageable pageable) {
        log.debug("list with page {}", pageable);

        if (pageable.getPageSize() > PAGE_MAX_SIZE) {
            throw new IllegalArgumentException("max page size exceeded");
        }

        Page<E> page = repository.findAll(pageable);
        List<D> content = page.stream().map(e -> dtoBuilder.convert(e)).collect(Collectors.toList());

        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    @Override
    public List<D> listAll() {
        log.debug("list all");

        return repository.findAll().stream().map(e -> dtoBuilder.convert(e)).collect(Collectors.toList());
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
            Page<E> page = ((JpaSpecificationExecutor<E>) repository).findAll(specification, pageable);
            List<D> content = page.stream().map(e -> dtoBuilder.convert(e)).collect(Collectors.toList());

            return new PageImpl<>(content, pageable, page.getTotalElements());
        }

        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<D> searchAll(Specification<E> specification) {
        log.debug("search all with spec {} ", specification);

        if (repository instanceof JpaSpecificationExecutor) {
            return ((JpaSpecificationExecutor<E>) repository).findAll(specification)
                .stream()
                .map(e -> dtoBuilder.convert(e))
                .collect(Collectors.toList());
        }

        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    @Override
    public long deleteAll(Specification<E> specification) {
        log.debug("delete all with spec {} ", specification);

        if (repository instanceof JpaSpecificationExecutor) {
            return ((JpaSpecificationExecutor<E>) repository).delete(specification);
        }

        throw new UnsupportedOperationException();
    }
}
