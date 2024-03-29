package it.smartcommunitylabdhub.commons.infrastructure;

import it.smartcommunitylabdhub.commons.models.entities.function.FunctionBaseSpec;
import it.smartcommunitylabdhub.commons.models.entities.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.entities.task.TaskBaseSpec;

/**
 * Given a function string a task and a executionDTO return a RunDTO
 */
public interface Builder<F extends FunctionBaseSpec, T extends TaskBaseSpec, R extends RunBaseSpec> {
    R build(F funSpec, T taskSpec, R runSpec);
}
