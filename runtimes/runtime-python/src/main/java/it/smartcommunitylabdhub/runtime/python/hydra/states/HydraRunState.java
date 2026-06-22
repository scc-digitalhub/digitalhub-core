package it.smartcommunitylabdhub.runtime.python.hydra.states;

import java.util.Optional;

import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.fsm.Transition;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runs.lifecycle.RunEvent;
import it.smartcommunitylabdhub.runs.lifecycle.RunState;
import it.smartcommunitylabdhub.runs.specs.RunBaseSpec;
import it.smartcommunitylabdhub.runs.specs.RunBaseStatus;
import it.smartcommunitylabdhub.runtimes.Runtime;
import it.smartcommunitylabdhub.runtimes.lifecycle.states.BaseRunState;

public class HydraRunState<X extends RunBaseSpec, Z extends RunBaseStatus, R extends RunRunnable> extends BaseRunState<X, Z, R> {

    public HydraRunState(String state, Runtime<X, Z, R> runtime) {
        super(state, runtime);
    }

        protected Transition.Builder<String, String, Run> toDelete() {
        //(DELETE)->DELETED
        return new Transition.Builder<String, String, Run>()
            .event(RunEvent.DELETE.name())
            .nextState(RunState.DELETED.name())
            .<R, R>withInternalLogic((currentState, nextState, event, run, runnable) -> {
                //runtime callback
                Optional
                    .ofNullable(runtime.onDeleted(run, runnable))
                    .ifPresent(status -> run.setStatus(MapUtils.mergeMultipleMaps(run.getStatus(), status.toMap())));

                return Optional.empty();
            });
    }

        protected Transition.Builder<String, String, Run> toDeleting() {
        //(DELETE)->DELETING
        return new Transition.Builder<String, String, Run>()
            .event(RunEvent.DELETE.name())
            .nextState(RunState.DELETING.name())
            .<R, R>withInternalLogic((currentState, nextState, event, run, runnable) -> {
                //delete via runtime
                return Optional.ofNullable(runtime.delete(run));
            });
    }
}
