package it.smartcommunitylabdhub.runtime.python.hydra.states;

import java.util.List;

import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.runs.lifecycle.RunState;
import it.smartcommunitylabdhub.runs.specs.RunBaseSpec;
import it.smartcommunitylabdhub.runs.specs.RunBaseStatus;

public class RunStateDeleting<S extends RunBaseSpec, Z extends RunBaseStatus, R extends RunRunnable>
    extends HydraRunState<S, Z, R> {

    public RunStateDeleting(it.smartcommunitylabdhub.runtimes.Runtime<S, Z, R> runtime) {
        super(RunState.DELETING.name(), runtime);
        //transitions
        txs = List.of(toDelete().build(), toError().build());
    }
}