package it.smartcommunitylabdhub.commons.infrastructure;

import it.smartcommunitylabdhub.commons.models.base.Executable;
import it.smartcommunitylabdhub.commons.models.base.ExecutableBaseSpec;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.models.entities.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.entities.run.RunBaseStatus;
import it.smartcommunitylabdhub.commons.models.entities.task.Task;
import jakarta.validation.constraints.NotNull;
import org.springframework.lang.Nullable;

/**
 * Runtime expose builder, run and parse method
 */
public interface Runtime<
    F extends ExecutableBaseSpec, S extends RunBaseSpec, Z extends RunBaseStatus, R extends RunRunnable
> {
    S build(@NotNull Executable execSpec, @NotNull Task taskSpec, @NotNull Run runSpec); // CREATED -> BUILT

    R run(@NotNull Run run); // BUILT -> READY

    R stop(@NotNull Run run); // X -> STOPPED

    R delete(@NotNull Run run); // X -> STOPPED

    @Nullable
    Z onRunning(@NotNull Run run, RunRunnable runnable);

    @Nullable
    Z onComplete(@NotNull Run run, RunRunnable runnable);

    @Nullable
    Z onError(@NotNull Run run, RunRunnable runnable);

    @Nullable
    Z onStopped(@NotNull Run run, RunRunnable runnable);

    @Nullable
    Z onDeleted(@NotNull Run run, @Nullable RunRunnable runnable);
}
