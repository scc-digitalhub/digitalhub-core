package it.smartcommunitylabdhub.runtime.python.hydra;

import it.smartcommunitylabdhub.core.lifecycle.BaseFsmFactory;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.python.hydra.states.RunStateCompleted;
import it.smartcommunitylabdhub.runtime.python.hydra.states.RunStateCreated;
import it.smartcommunitylabdhub.runtime.python.hydra.states.RunStateDeleted;
import it.smartcommunitylabdhub.runtime.python.hydra.states.RunStateDeleting;
import it.smartcommunitylabdhub.runtime.python.hydra.states.RunStateError;
import it.smartcommunitylabdhub.runtime.python.hydra.states.RunStatePending;
import it.smartcommunitylabdhub.runtime.python.hydra.states.RunStateReady;
import it.smartcommunitylabdhub.runtime.python.hydra.states.RunStateRunning;
import it.smartcommunitylabdhub.runtime.python.hydra.states.RunStateStop;
import it.smartcommunitylabdhub.runtime.python.hydra.states.RunStateStopped;
import it.smartcommunitylabdhub.runtime.python.hydra.states.RunStateBuilt;

public class HydraFsmFactory extends BaseFsmFactory<Run> {

    public HydraFsmFactory(HydraRuntime runtime) {
        super(
            new RunStateBuilt<>(runtime),
            new RunStateCompleted<>(runtime),
            new RunStateCreated<>(runtime),
            new RunStateDeleted<>(runtime),
            new RunStateDeleting<>(runtime),
            new RunStateError<>(runtime),
            new RunStateReady<>(runtime),
            new RunStatePending<>(runtime),
            new RunStateRunning<>(runtime),
            new RunStateStop<>(runtime),
            new RunStateStopped<>(runtime)
        );
    }
}
