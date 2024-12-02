package it.smartcommunitylabdhub.core.fsm;

import it.smartcommunitylabdhub.commons.infrastructure.Actuator;
import it.smartcommunitylabdhub.commons.models.trigger.Trigger;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerBaseSpec;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerBaseStatus;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerRunBaseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;

@AllArgsConstructor
@Builder
public class TriggerContext {

    public final Actuator<
        ? extends TriggerBaseSpec,
        ? extends TriggerBaseStatus,
        ? extends TriggerRunBaseStatus
    > actuator;
    public final Trigger trigger;
}
