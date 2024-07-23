package it.smartcommunitylabdhub.core.components.cloud;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RunCloudListener {
    private final SimpMessagingTemplate messagingTemplate;

    public RunCloudListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    //TODO inviare nel messaggio anche tutta la run come binary, implementare controller, autenticazione, dispatch per utente
    @Async
    @EventListener
    public void broadcast(CloudEntityEvent<Run> event) {
        log.info("----catched run event, {}", event.getDto().getId());
        try {
            Run run = event.getDto();
            log.info("----run dto status: {}", run.getStatus().toString());
            String message = String.join(":", run.getId(), run.getStatus().get("state").toString());
            messagingTemplate.convertAndSend("/topic/runs", message);
        } catch (MessagingException e) {
            log.error("Error sending message", e.getMessage());
        }
    }
}
