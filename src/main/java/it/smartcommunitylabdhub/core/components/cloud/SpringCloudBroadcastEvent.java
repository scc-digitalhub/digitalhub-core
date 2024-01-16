package it.smartcommunitylabdhub.core.components.cloud;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.smartcommunitylabdhub.core.components.cloud.events.EntitySavedEvent;
import it.smartcommunitylabdhub.core.models.base.interfaces.BaseEntity;
import it.smartcommunitylabdhub.core.utils.jackson.JacksonMapper;
import it.smartcommunitylabdhub.core.utils.jackson.mixins.CborMixin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "cloud-stream.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class SpringCloudBroadcastEvent {

    @Autowired
    RabbitTemplate rabbitTemplate;

    @EventListener
    public <T extends BaseEntity> void handleEntitySavedEvent(EntitySavedEvent<T> event) {
        // Broadcast event on rabbit amqp
        try {
            JacksonMapper.OBJECT_MAPPER.addMixIn(event.getEventClass(), CborMixin.class);
            String serializedEntity = JacksonMapper.OBJECT_MAPPER
                    .writeValueAsString(event.getEntity());


            rabbitTemplate.convertAndSend(serializedEntity);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }


        ////      Was used for spring cloud
        //        @Autowired
        //        private StreamBridge streamBridge;
        //
        //        streamBridge.send("entity-out-0", serializedEntity);
        //        streamBridge.send("entityOutput-kafka-out-0", message);
    }

}
