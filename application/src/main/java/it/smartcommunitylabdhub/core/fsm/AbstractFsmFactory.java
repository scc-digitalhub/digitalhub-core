package it.smartcommunitylabdhub.core.fsm;

import it.smartcommunitylabdhub.fsm.Fsm;
import it.smartcommunitylabdhub.fsm.FsmState;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * State machine factory
 */
@Slf4j
public abstract class AbstractFsmFactory<S, E, C, I> implements Fsm.Factory<S, E, C, I> {

    //states are defined only *once*
    private final List<FsmState.Builder<S, E, C, I>> stateBuilders;

    protected AbstractFsmFactory(List<FsmState.Builder<S, E, C, I>> stateBuilders) {
        this.stateBuilders = stateBuilders != null ? stateBuilders : Collections.emptyList();
    }

    /**
     * Create and configure the StateMachine for managing the state transitions of a Run.
     *
     * @param initialState   The initial state for the StateMachine.
     * @param initialContext The initial context for the StateMachine.
     * @return The configured StateMachine instance.
     */
    public Fsm<S, E, C, I> create(S initialState, C context) {
        // Create a new StateMachine builder with the initial state and context
        Fsm.Builder<S, E, C, I> builder = new Fsm.Builder<>(initialState, context);

        //add all states
        stateBuilders.forEach(sb -> {
            FsmState<S, E, C, I> state = sb.build();
            builder.withState(state.getState(), state);
        });

        //build to seal
        return builder.build();
    }
}
