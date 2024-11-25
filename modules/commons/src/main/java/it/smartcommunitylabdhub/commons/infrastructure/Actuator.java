package it.smartcommunitylabdhub.commons.infrastructure;

import it.smartcommunitylabdhub.commons.models.trigger.Trigger;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerBaseSpec;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerBaseStatus;
import jakarta.validation.constraints.NotNull;

public interface Actuator<S extends TriggerBaseSpec, Z extends TriggerBaseStatus> {
    Z register(@NotNull Trigger trigger);
    Z unregister(@NotNull Trigger trigger);
}
