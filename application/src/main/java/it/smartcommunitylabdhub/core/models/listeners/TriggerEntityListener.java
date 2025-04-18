package it.smartcommunitylabdhub.core.models.listeners;

import org.springframework.context.event.EventListener;
import org.springframework.core.convert.converter.Converter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import it.smartcommunitylabdhub.commons.models.trigger.Trigger;
import it.smartcommunitylabdhub.core.models.entities.TriggerEntity;
import it.smartcommunitylabdhub.core.models.events.EntityEvent;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class TriggerEntityListener extends AbstractEntityListener<TriggerEntity, Trigger> {

    public TriggerEntityListener(Converter<TriggerEntity, Trigger> converter) {
        super(converter);
    }

    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void receive(EntityEvent<TriggerEntity> event) {
        if (event.getEntity() == null) {
            return;
        }

        log.debug("receive event for {} {}", clazz.getSimpleName(), event.getAction());

        TriggerEntity entity = event.getEntity();
        TriggerEntity prev = event.getPrev();
        if (log.isTraceEnabled()) {
            log.trace("{}: {}", clazz.getSimpleName(), String.valueOf(entity));
        }

        //handle
        super.handle(event);

        //always broadcast updates
        super.broadcast(event);

        //TODO update project?

        if (entity.getUpdatedBy() != null) {
            //notify user event if either: prev == null (for create/delete), prev != null and state has changed (update)
            if (prev == null || (prev != null && prev.getState() != entity.getState())) {
                //notify user
                super.notify(entity.getUpdatedBy(), event);

                if (!entity.getUpdatedBy().equals(entity.getCreatedBy())) {
                    //notify owner
                    super.notify(entity.getCreatedBy(), event);
                }
            }
        }
    }
}
