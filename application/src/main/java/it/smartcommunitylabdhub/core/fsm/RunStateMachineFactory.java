package it.smartcommunitylabdhub.core.fsm;

import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.fsm.Fsm;
import it.smartcommunitylabdhub.fsm.FsmState;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * State machine factory for runs
 */
@Component
@Slf4j
public class RunStateMachineFactory implements Fsm.Factory<State, RunEvent, RunContext, RunRunnable> {

    private List<FsmState<State, RunEvent, RunContext, RunRunnable>> states = new ArrayList<>();

    public RunStateMachineFactory(List<FsmState<State, RunEvent, RunContext, RunRunnable>> states) {
        this.states = states;
    }

    /**
     * Create and configure the StateMachine for managing the state transitions of a Run.
     *
     * @param initialState   The initial state for the StateMachine.
     * @param initialContext The initial context for the StateMachine.
     * @return The configured StateMachine instance.
     */
    public Fsm<State, RunEvent, RunContext, RunRunnable> create(State initialState, RunContext context) {
        // Create a new StateMachine builder with the initial state and context
        Fsm.Builder<State, RunEvent, RunContext, RunRunnable> builder = new Fsm.Builder<>(initialState, context);

        //add all states
        states.forEach(state -> builder.withState(state.getState(), state));

        //build to seal
        return builder.build();
    }
    // /**
    //  * Create and configure the StateMachine for managing the state transitions of a Run.
    //  *
    //  * @param initialState   The initial state for the StateMachine.
    //  * @param initialContext The initial context for the StateMachine.
    //  * @return The configured StateMachine instance.
    //  */
    // public Fsm<State, RunEvent, RunContext, RunRunnable> build(
    //     Run run,
    //     Runtime<
    //         ? extends ExecutableBaseSpec,
    //         ? extends RunBaseSpec,
    //         ? extends RunBaseStatus,
    //         ? extends RunRunnable
    //     > runtime
    // ) {
    //     //initial state is run current state
    //     State initialState = State.valueOf(StatusFieldAccessor.with(run.getStatus()).getState());

    //     // Create a new StateMachine builder with the initial state and context
    //     Fsm.Builder<State, RunEvent, RunContext, RunRunnable> builder = new Fsm.Builder<>(
    //         initialState,
    //         RunContext.builder().run(run).runtime(runtime).build()
    //     );

    //     // Configure the StateMachine with the defined states and transitions
    //     builder
    //         .withState(
    //             State.CREATED,
    //             new FsmState.Builder<State, RunEvent, RunContext, RunRunnable>(State.CREATED)
    //                 .withTransitions(
    //                     List.of(
    //                         new Transition<>(RunEvent.BUILD, State.BUILT),
    //                         new Transition<>(RunEvent.ERROR, State.ERROR),
    //                         new Transition<>(RunEvent.DELETING, State.DELETING)
    //                     )
    //                 )
    //                 .build()
    //         )
    //         .withState(
    //             State.BUILT,
    //             new FsmState.Builder<State, RunEvent, RunContext, RunRunnable>(State.BUILT)
    //                 .withTransitions(
    //                     List.of(
    //                         new Transition<>(RunEvent.RUN, State.READY),
    //                         new Transition<>(RunEvent.ERROR, State.ERROR),
    //                         new Transition<>(RunEvent.DELETING, State.DELETING)
    //                     )
    //                 )
    //                 .build()
    //         )
    //         .withState(
    //             State.READY,
    //             new FsmState.Builder<State, RunEvent, RunContext, RunRunnable>(State.READY)
    //                 .withTransitions(
    //                     List.of(
    //                         new Transition<>(RunEvent.EXECUTE, State.RUNNING),
    //                         new Transition<>(RunEvent.PENDING, State.READY),
    //                         new Transition<>(RunEvent.ERROR, State.ERROR),
    //                         new Transition<>(RunEvent.DELETING, State.DELETING)
    //                     )
    //                 )
    //                 .build()
    //         )
    //         .withState(
    //             State.STOP,
    //             new FsmState.Builder<State, RunEvent, RunContext, RunRunnable>(State.STOP)
    //                 .withTransitions(
    //                     List.of(
    //                         new Transition<>(RunEvent.STOP, State.STOPPED),
    //                         new Transition<>(RunEvent.ERROR, State.ERROR),
    //                         new Transition<>(RunEvent.DELETING, State.DELETING)
    //                     )
    //                 )
    //                 .build()
    //         )
    //         .withState(
    //             State.STOPPED,
    //             new FsmState.Builder<State, RunEvent, RunContext, RunRunnable>(State.STOPPED)
    //                 .withTransitions(
    //                     List.of(
    //                         new Transition<>(RunEvent.RESUME, State.RESUME),
    //                         new Transition<>(RunEvent.ERROR, State.ERROR),
    //                         new Transition<>(RunEvent.DELETING, State.DELETING)
    //                     )
    //                 )
    //                 .build()
    //         )
    //         .withState(
    //             State.RESUME,
    //             new FsmState.Builder<State, RunEvent, RunContext, RunRunnable>(State.RESUME)
    //                 .withTransitions(
    //                     List.of(
    //                         new Transition<>(RunEvent.ERROR, State.ERROR),
    //                         new Transition<>(RunEvent.DELETING, State.DELETING),
    //                         new Transition<>(RunEvent.EXECUTE, State.RUNNING)
    //                     )
    //                 )
    //                 .build()
    //         )
    //         .withState(
    //             State.RUNNING,
    //             new FsmState.Builder<State, RunEvent, RunContext, RunRunnable>(State.RUNNING)
    //                 .withTransitions(
    //                     List.of(
    //                         new Transition<>(RunEvent.LOOP, State.RUNNING),
    //                         new Transition<>(RunEvent.COMPLETE, State.COMPLETED),
    //                         new Transition<>(RunEvent.ERROR, State.ERROR),
    //                         new Transition<>(RunEvent.STOP, State.STOP),
    //                         new Transition<>(RunEvent.DELETING, State.DELETING)
    //                     )
    //                 )
    //                 .build()
    //         )
    //         .withState(
    //             State.COMPLETED,
    //             new FsmState.Builder<State, RunEvent, RunContext, RunRunnable>(State.COMPLETED)
    //                 .withTransitions(
    //                     List.of(
    //                         new Transition<>(RunEvent.DELETING, State.DELETING),
    //                         new Transition<>(RunEvent.DELETING, State.DELETED)
    //                     )
    //                 )
    //                 .build()
    //         )
    //         .withState(
    //             State.ERROR,
    //             new FsmState.Builder<State, RunEvent, RunContext, RunRunnable>(State.ERROR)
    //                 .withTransitions(
    //                     List.of(
    //                         new Transition<>(RunEvent.DELETING, State.DELETING),
    //                         new Transition<>(RunEvent.DELETING, State.DELETED)
    //                     )
    //                 )
    //                 .build()
    //         )
    //         .withState(
    //             State.DELETING,
    //             new FsmState.Builder<State, RunEvent, RunContext, RunRunnable>(State.DELETING)
    //                 .withTransitions(List.of(new Transition<>(RunEvent.DELETING, State.DELETED)))
    //                 .build()
    //         )
    //         .withState(
    //             State.DELETED,
    //             new FsmState.Builder<State, RunEvent, RunContext, RunRunnable>(State.DELETED).build()
    //         )
    //         .build();

    //     // Return the builder

    //     return builder.build();
    // }
}
