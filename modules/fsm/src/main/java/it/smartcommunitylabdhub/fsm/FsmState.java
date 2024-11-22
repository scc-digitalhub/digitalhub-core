package it.smartcommunitylabdhub.fsm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;

/*
 * Compact connected state representation, grouping state + transactions towards different states
 */
public class FsmState<S, E, C, I> {

    @Getter
    protected final S state;

    @Getter
    protected final List<Transition<S, E, C, I>> transitions;

    public FsmState(S state, List<Transition<S, E, C, I>> transactions) {
        this.state = state;
        this.transitions = transactions;
    }

    /**
     * Get the transition associated with the specified event, if present
     * @param event
     * @return
     */
    public Optional<Transition<S, E, C, I>> getTransitionForEvent(E event) {
        return transitions.stream().filter(t -> t.getEvent().equals(event)).findFirst();
    }

    /**
     * Get the transition associated with a given next state, if present
     *
     * @param nextState The next state for which to retrieve the transition event.
     * @return An Optional containing the transition event if found, or an empty Optional if not
     * found.
     */
    public Optional<Transition<S, E, C, I>> getTransitionForNext(S nextState) {
        return transitions.stream().filter(t -> t.getNextState().equals(nextState)).findFirst();
    }

    @FunctionalInterface
    public static interface Builder<S, E, C, I> {
        FsmState<S, E, C, I> build();
    }
    // /**
    //  * A builder class for constructing FsmState objects.
    //  *
    //  * @param <S> The type of the states.
    //  * @param <E> The type of the events.
    //  * @param <C> The type of the context.
    //  */
    // public static class Builder<S, E, C, I> {

    //     private final List<Transition<S, E, C, I>> transitions;
    //     private final S state;

    //     /**
    //      * Constructs a new StateBuilder object.
    //      *
    //      * @param state the name of the state
    //      */
    //     public Builder(S state) {
    //         this.state = state;
    //         transitions = new ArrayList<>();
    //     }

    //     /**
    //      * Add a transaction associated with this state.
    //      *
    //      * @param transaction The transaction to add.
    //      * @return The StateBuilder instance.
    //      */
    //     public Builder<S, E, C, I> withTransition(Transition<S, E, C, I> transaction) {
    //         transitions.add(transaction);
    //         return this;
    //     }

    //     /**
    //      * Add a transaction associated with this state.
    //      *
    //      * @param transactionList List of transactions
    //      * @return The StateBuilder instance.
    //      */
    //     public Builder<S, E, C, I> withTransitions(List<Transition<S, E, C, I>> transactionList) {
    //         transitions.addAll(transactionList);
    //         return this;
    //     }

    //     /**
    //      * Finalize the state definition and return the parent builder.
    //      *
    //      * @return The parent builder instance.
    //      */
    //     public FsmState<S, E, C, I> build() {
    //         return new FsmState<>(state, transitions);
    //     }
    // }
}
