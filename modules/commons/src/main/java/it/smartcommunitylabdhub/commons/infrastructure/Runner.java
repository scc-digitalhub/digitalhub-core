package it.smartcommunitylabdhub.commons.infrastructure;

import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import javax.validation.constraints.NotNull;

/**
 * Prender il RunDTO e produce il Runnable
 */
@FunctionalInterface
public interface Runner<R extends RunRunnable> {
    R produce(@NotNull Run runDTO);
}
