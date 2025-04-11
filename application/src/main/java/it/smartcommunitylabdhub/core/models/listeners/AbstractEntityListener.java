package it.smartcommunitylabdhub.core.models.listeners;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.core.components.cloud.CloudEntityEvent;
import it.smartcommunitylabdhub.core.models.base.BaseEntity;
import it.smartcommunitylabdhub.core.models.events.EntityEvent;
import it.smartcommunitylabdhub.core.models.indexers.EntityIndexer;
import it.smartcommunitylabdhub.core.relationships.BaseEntityRelationshipsManager;
import it.smartcommunitylabdhub.core.websocket.UserNotificationEntityEvent;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.convert.converter.Converter;

@Slf4j
public abstract class AbstractEntityListener<E extends BaseEntity, T extends BaseDTO> {

    protected final Converter<E, T> converter;
    protected ApplicationEventPublisher eventPublisher;
    protected final Class<T> clazz;

    protected EntityIndexer<E> indexer;

    protected BaseEntityRelationshipsManager<E> relationshipsManager;

    protected AbstractEntityListener(Converter<E, T> converter) {
        this.converter = converter;
        clazz = extractClass();
    }

    @Autowired
    public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Autowired(required = false)
    public void setIndexer(EntityIndexer<E> indexer) {
        this.indexer = indexer;
    }

    @Autowired(required = false)
    public void setRelationshipsManager(BaseEntityRelationshipsManager<E> manager) {
        this.relationshipsManager = manager;
    }

    protected void handle(EntityEvent<E> event) {
        log.debug("receive event for {} {}", clazz.getSimpleName(), event.getAction());

        E entity = event.getEntity();
        if (log.isTraceEnabled()) {
            log.trace("{}: {}", clazz.getSimpleName(), String.valueOf(entity));
        }

        if (entity == null) {
            return;
        }

        T dto = converter.convert(entity);
        switch (event.getAction()) {
            case CREATE:
                {
                    onCreate(entity, dto);
                    break;
                }
            case UPDATE:
                {
                    onUpdate(entity, dto);
                    break;
                }
            case DELETE:
                {
                    onDelete(entity, dto);
                    break;
                }
            default:
                break;
        }
    }

    protected void broadcast(EntityEvent<E> event) {
        log.debug("broadcast event for {} {}", clazz.getSimpleName(), event.getAction());
        //publish external event
        if (eventPublisher != null) {
            E entity = event.getEntity();
            if (log.isTraceEnabled()) {
                log.trace("{}: {}", clazz.getSimpleName(), String.valueOf(entity));
            }

            if (entity == null) {
                return;
            }

            T dto = converter.convert(entity);

            log.debug("publish cloud event: {} for {} {}", event.getAction(), clazz.getSimpleName(), dto.getId());
            CloudEntityEvent<T> cloud = new CloudEntityEvent<>(dto, clazz, event.getAction());
            if (log.isTraceEnabled()) {
                log.trace("cloud event: {}", String.valueOf(cloud));
            }

            eventPublisher.publishEvent(cloud);
        }
    }

    protected void notify(String user, EntityEvent<E> event) {
        log.debug("notify event for {} {}", clazz.getSimpleName(), event.getAction());
        //publish external event
        if (eventPublisher != null) {
            E entity = event.getEntity();
            if (log.isTraceEnabled()) {
                log.trace("{}: {}", clazz.getSimpleName(), String.valueOf(entity));
            }

            if (entity == null) {
                return;
            }

            T dto = converter.convert(entity);

            log.debug("publish notify event: {} for {} {}", event.getAction(), clazz.getSimpleName(), dto.getId());
            UserNotificationEntityEvent<T> cloud = new UserNotificationEntityEvent<>(
                user,
                dto,
                clazz,
                event.getAction()
            );
            if (log.isTraceEnabled()) {
                log.trace("notify event: {}", String.valueOf(cloud));
            }

            eventPublisher.publishEvent(cloud);
        }
    }

    protected void onCreate(E entity, T dto) {
        log.debug("onCreate for {}", entity.getId());
        //index
        if (indexer != null) {
            try {
                log.debug("index document with id {}", entity.getId());
                indexer.index(entity);
            } catch (Exception e) {
                log.error("error with solr: {}", e.getMessage());
            }
        }

        //relationships
        if (relationshipsManager != null) {
            try {
                log.debug("set relationship for entity with id {}", entity.getId());
                relationshipsManager.register(entity);
            } catch (StoreException e) {
                log.error("error with relationshipsManager: {}", e.getMessage());
            }
        }
    }

    protected void onUpdate(E entity, T dto) {
        log.debug("onUpdate for {}", entity.getId());
        //index
        if (indexer != null) {
            try {
                log.debug("index document with id {}", entity.getId());
                indexer.index(entity);
            } catch (Exception e) {
                log.error("error with solr: {}", e.getMessage());
            }
        }

        //relationships
        if (relationshipsManager != null) {
            try {
                log.debug("set relationship for entity with id {}", entity.getId());
                relationshipsManager.register(entity);
            } catch (StoreException e) {
                log.error("error with relationshipsManager: {}", e.getMessage());
            }
        }
    }

    protected void onDelete(E entity, T dto) {
        log.debug("onDelete for {}", entity.getId());

        //TODO! index

        //relationships
        if (relationshipsManager != null) {
            try {
                log.debug("clear relationship for entity with id {}", entity.getId());
                relationshipsManager.clear(entity);
            } catch (StoreException e) {
                log.error("error with relationshipsManager: {}", e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected Class<T> extractClass() {
        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[1];
        return (Class<T>) t;
    }
}
