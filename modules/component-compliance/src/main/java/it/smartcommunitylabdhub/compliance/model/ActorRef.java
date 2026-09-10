package it.smartcommunitylabdhub.compliance.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Reference to a human user, service account, or automated agent that performed an action. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActorRef {

    @Schema(title = "fields.compliance.actor_ref.kind.title", description = "fields.compliance.actor_ref.kind.description")
    private ActorKind kind;
    @Schema(title = "fields.compliance.actor_ref.id.title", description = "fields.compliance.actor_ref.id.description")
    /** e.g., OIDC subject, service principal URI. */
    private String id;

    public enum ActorKind {
        HUMAN,
        SERVICE,
        AGENT,
    }
}
