package it.smartcommunitylabdhub.commons.infrastructure;

import it.smartcommunitylabdhub.commons.models.trigger.Trigger;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerBaseSpec;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerBaseStatus;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerRunBaseStatus;
import jakarta.validation.constraints.NotNull;

public interface Actuator<S extends TriggerBaseSpec, Z extends TriggerBaseStatus, R extends TriggerRunBaseStatus> {
    Z run(@NotNull Trigger trigger);
    Z stop(@NotNull Trigger trigger);
    R onFire(@NotNull Trigger trigger, TriggerRun run);
}
