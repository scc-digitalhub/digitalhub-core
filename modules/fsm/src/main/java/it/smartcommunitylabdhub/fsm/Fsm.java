/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * StateMachine.java
 * <p>
 * This class represents a State Machine that handles the flow of states and transitions based on
 * events and guards. It allows the definition of states and transitions along with their associated
 * actions and guards.
 *
 * @param <S> The type of the states.
 * @param <E> The type of the events.
 * @param <C> The type of the context.
 */

package it.smartcommunitylabdhub.fsm;

import it.smartcommunitylabdhub.fsm.exceptions.InvalidTransitionException;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

/*
 * A FSM which manages transitions (w/effects) over states
 * i.e a graph of nodes==states with edges==transitions
 *
 * FSM persists a Context available to all effects.
 * S -> states
 * E -> events for transitions
 * C -> context
 * I -> input for traversal
 */
@Slf4j
public class Fsm<S, E, C> {

    private static final long LOCK_WAIT = 180L; // seconds

    // single lock to ensure atomic actions
    private final ReentrantLock stateLock = new ReentrantLock();

    //current state
    @Getter
    private S currentState;

    //graph as a map state -> definitions of state+transitions
    private Map<S, FsmState<S, E, C>> states;

    //context
    private final C context;

    public @NotNull C getContext() {
        //NOTE: context shouldn't be modified outside of the FSM
        //TODO enforce immutability via serialization
        return context;
    }

    /**
     * Constructor to create a StateMachine with the initial state and context.
     *
     * @param initialState   The initial state of the StateMachine.
     * @param initialContext The initial context for the StateMachine.
     */
    protected Fsm(@NotNull S initialState, @NotNull C initialContext) {
        Assert.notNull(initialContext, "context must not be null");
        Assert.notNull(initialState, "initial state must not be null");

        this.currentState = initialState;
        this.context = initialContext;
        this.states = new ConcurrentHashMap<>();
    }

    /**
     * Static builder method to create a new StateMachine.
     *
     * @param initialState   The initial state of the StateMachine.
     * @param initialContext The initial context for the StateMachine.
     * @return A new Builder instance to configure and build the StateMachine.
     */
    public static <S, E, C> Builder<S, E, C> builder(S initialState, C initialContext) {
        return new Builder<>(initialState, initialContext);
    }

    public FsmState<S, E, C> getState(S state) {
        return states.get(state);
    }

    /**
     * Transition the state machine from *current state* to destination, if adjacent
     * Performs transitions between states while walking the path
     *
     * @param targetState The state to transition to.
     * @param input       The input associated with the transition.
     * @param <R>         The type of the input.
     */
    public <I, R> Optional<R> goToState(S targetState, @Nullable I input) throws InvalidTransitionException {
        log.debug("transition to state {}", targetState);

        return acquireLock()
            .flatMap(lockAcquired -> {
                if (Boolean.TRUE.equals(lockAcquired)) {
                    log.debug("lock acquired for transition to {}", targetState);

                    try {
                        //check if there is an adjacent state for target
                        FsmState<S, E, C> stateDefinition = states.get(currentState);
                        if (stateDefinition == null) {
                            throw new InvalidTransitionException(currentState.toString(), null);
                        }

                        Optional<Transition<S, E, C>> transition = stateDefinition.getTransitionForNext(targetState);
                        if (transition.isEmpty()) {
                            // No valid path exists; transition to the error state
                            throw new InvalidTransitionException(currentState.toString(), null);
                        }

                        // Execute the transition to next state and collect result.
                        return execute(transition.get(), input);
                    } finally {
                        stateLock.unlock();
                    }
                }
                return Optional.empty();
            });
    }

    public <I, R> Optional<R> perform(E event, @Nullable I input) throws InvalidTransitionException {
        log.debug("transition for event {}", event);

        return acquireLock()
            .flatMap(lockAcquired -> {
                if (Boolean.TRUE.equals(lockAcquired)) {
                    log.debug("lock acquired for transition for event {}", event);
                    try {
                        //check if there is a transition for this event connected to current state
                        FsmState<S, E, C> stateDefinition = states.get(currentState);
                        if (stateDefinition == null) {
                            throw new InvalidTransitionException(currentState.toString(), null);
                        }

                        Optional<Transition<S, E, C>> transition = stateDefinition.getTransitionForEvent(event);
                        if (transition.isEmpty()) {
                            // No valid path exists; transition to the error state
                            throw new InvalidTransitionException(currentState.toString(), null);
                        }

                        // Execute the transition to next state and collect result.
                        return execute(transition.get(), input);
                    } finally {
                        stateLock.unlock();
                    }
                }
                return Optional.empty();
            });
    }

    private <I, R> Optional<R> execute(@NotNull Transition<S, E, C> transition, @Nullable I input) {
        S nextState = transition.getNextState();
        E event = transition.getEvent();

        log.trace("execute transition from {} for event {} towards {}", currentState, event, nextState);

        //apply logic (note explicit type cast!)
        Optional<TransitionLogic<S, E, C, I, R>> fn = transition.getInternalLogic();
        Optional<R> result = fn.flatMap(callback -> callback.apply(currentState, nextState, event, context, input));

        // Update the current state
        currentState = nextState;

        return result;
    }

    /**
     * Attempt to acquire a lock with a timeout.
     *
     * @return An {@code Optional<Boolean>} representing the lock acquisition result. If the lock is
     * acquired successfully, it contains {@code true}; otherwise, it contains
     * {@code false}.
     */
    private Optional<Boolean> acquireLock() {
        try {
            boolean lockAcquired = stateLock.tryLock(LOCK_WAIT, TimeUnit.SECONDS);
            return Optional.of(lockAcquired); // Return Optional with lock acquisition result.
        } catch (InterruptedException e) {
            log.error(e.getMessage());
            return Optional.empty(); // Return Optional.empty() in case of an interruption or
            // exception.
        }
    }

    //factory
    @FunctionalInterface
    public static interface Factory<S, E, C> {
        Fsm<S, E, C> create(S initialState, C context);
    }

    // Builder
    public static class Builder<S, E, C> {

        private final S currentState;

        @Getter
        private final Map<S, FsmState<S, E, C>> states;

        private final C initialContext;

        public Builder(S initialState, C initialContext) {
            this.currentState = initialState;
            this.initialContext = initialContext;
            this.states = new ConcurrentHashMap<>();
        }

        /**
         * Adds a state and its definition to the builder's configuration.
         *
         * @param state The state to add.
         * @return This builder instance of the state, allowing for method chaining.
         */

        public Builder<S, E, C> withState(S state, FsmState<S, E, C> stateDefinition) {
            states.put(state, stateDefinition);
            return this;
        }

        public Fsm<S, E, C> build() {
            Fsm<S, E, C> stateMachine = new Fsm<>(currentState, initialContext);
            stateMachine.states = Collections.unmodifiableMap(states);

            return stateMachine;
        }
    }
}
