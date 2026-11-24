package it.smartcommunitylabdhub.core.audit;

import it.smartcommunitylabdhub.core.events.EntityEvent;
import it.smartcommunitylabdhub.core.persistence.BaseEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class AuditEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void receive(EntityEvent<? extends BaseEntity> event) {
        if (event.getEntity() == null) {
            return;
        }

        log.warn("Received event for entity: {}", event.getEntity());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.warn("Authenticated user: {}", authentication != null ? authentication.getName() : "anonymous");
    }
}
