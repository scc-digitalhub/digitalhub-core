package it.smartcommunitylabdhub.commons.infrastructure;

import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.models.entities.run.RunBaseStatus;

@FunctionalInterface
public interface RunProcessor<Z extends RunBaseStatus> {

    Z process(Run run, RunRunnable runRunnable, RunBaseStatus status);
}
