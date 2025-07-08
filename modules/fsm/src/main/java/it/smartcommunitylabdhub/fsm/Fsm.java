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
public class Fsm<S, E, C, I> {

    // single lock to ensure atomic actions
    private final ReentrantLock stateLock = new ReentrantLock();

    //current state
    @Getter
    private S currentState;

    //graph as a map state -> definitions of state+transitions
    private Map<S, FsmState<S, E, C, I>> states;

    //context
    private C context;

    /**
     * Default constructor to create an empty StateMachine.
     * Hidden, FSM should be built
     */
    private Fsm() {}

    /**
     * Constructor to create a StateMachine with the initial state and context.
     *
     * @param initialState   The initial state of the StateMachine.
     * @param initialContext The initial context for the StateMachine.
     */
    protected Fsm(S initialState, C initialContext) {
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
    public static <S, E, C, I> Builder<S, E, C, I> builder(S initialState, C initialContext) {
        return new Builder<>(initialState, initialContext);
    }

    public FsmState<S, E, C, I> getState(S state) {
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
    public <R> Optional<R> goToState(S targetState, @Nullable I input) throws InvalidTransitionException {
        log.debug("transition to state {}", targetState);

        return acquireLock()
            .flatMap(lockAcquired -> {
                if (Boolean.TRUE.equals(lockAcquired)) {
                    log.debug("lock acquired for transition to {}", targetState);

                    try {
                        //check if there is an adjacent state for target
                        FsmState<S, E, C, I> stateDefinition = states.get(currentState);
                        if (stateDefinition == null) {
                            throw new InvalidTransitionException(currentState.toString(), null);
                        }

                        Optional<Transition<S, E, C, I>> transition = stateDefinition.getTransitionForNext(targetState);
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

    // public <R> Optional<R> goToStateViaPath(S targetState, @Nullable I input) throws InvalidTransactionException {
    //     log.debug("transition to state {}", targetState);

    //     return acquireLock()
    //         .flatMap(lockAcquired -> {
    //             if (Boolean.TRUE.equals(lockAcquired)) {
    //                 log.debug("lock acquired for transition to {}", targetState);

    //                 try {
    //                     // Check if a valid path exists from the current state to the target state
    //                     List<S> path = findPath(currentState, targetState);
    //                     if (log.isTraceEnabled()) {
    //                         log.trace("path found for transition to {}:{}", targetState, String.valueOf(path));
    //                     }

    //                     if (path.isEmpty()) {
    //                         // No valid path exists; transition to the error state
    //                         throw new InvalidTransactionException(currentState.toString(), targetState.toString());
    //                     }

    //                     //if path is single and state matches we are in the same state
    //                     //no transition available
    //                     if (path.size() == 1 && path.get(0).equals(targetState)) {
    //                         log.debug("found path from {} to the same state", targetState);
    //                         throw new InvalidTransactionException(currentState.toString(), targetState.toString());
    //                     }

    //                     //if path is single and state does not match we have an error
    //                     //we should have a path from current to next
    //                     if (path.size() == 1 && !path.get(0).equals(targetState)) {
    //                         log.error("error with path from {} to {}", currentState, targetState);
    //                         throw new InvalidTransactionException(currentState.toString(), targetState.toString());
    //                     }

    //                     //sanity check: avoid loops
    //                     //DISABLED, without DFS if we find a loop it is legitimate
    //                     // Set<S> route = new HashSet<>(path);
    //                     // if (path.size() > route.size()) {
    //                     //     log.error("loops detected in path to {}", targetState);
    //                     //     throw new InvalidTransactionException(currentState.toString(), targetState.toString());
    //                     // }

    //                     // only a single step is allowed for a single transition
    //                     if (path.size() > 2) {
    //                         log.debug("path from {} to {} has more than 1-step", currentState, targetState);
    //                         throw new InvalidTransactionException(currentState.toString(), targetState.toString());
    //                     }

    //                     // Execute the transition and collect result.
    //                     return execute(path.get(0), path.get(1), input);
    //                 } finally {
    //                     stateLock.unlock();
    //                 }
    //             }
    //             return Optional.empty();
    //         });
    // }

    /**
     * Transition from *current state* over a path towards a new state, performing the transition
     * @param <R>
     * @param event
     * @param input
     * @return
     * @throws InvalidTransitionException
     */
    public <R> Optional<R> perform(E event, @Nullable I input) throws InvalidTransitionException {
        log.debug("transition for event {}", event);

        return acquireLock()
            .flatMap(lockAcquired -> {
                if (Boolean.TRUE.equals(lockAcquired)) {
                    log.debug("lock acquired for transition for event {}", event);
                    try {
                        //check if there is a transition for this event connected to current state
                        FsmState<S, E, C, I> stateDefinition = states.get(currentState);
                        if (stateDefinition == null) {
                            throw new InvalidTransitionException(currentState.toString(), null);
                        }

                        Optional<Transition<S, E, C, I>> transition = stateDefinition.getTransitionForEvent(event);
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

    // /**
    //  * Find a path from the source state to the target state in the state machine.
    //  * <p>
    //  * This method initiates a depth-first search (DFS) to explore the state machine's transitions
    //  * and find a valid path from the source state to the target state.
    //  *
    //  * @param sourceState The starting state of the path.
    //  * @param targetState The state to reach.
    //  * @return A list of states representing a valid path from the source state to the target state,
    //  * or an empty list if no valid path is found.
    //  */
    // private <R> List<S> findPath(S sourceState, S targetState) {
    //     // Set<S> visited = new HashSet<>();
    //     LinkedList<S> path = new LinkedList<>();
    //     path.addFirst(sourceState);

    //     // If the current state is the target state, a valid path is found
    //     //DISABLED, this is wrong, we are looking for a transition TO dest!
    //     // if (sourceState.equals(targetState)) {
    //     //     return path;
    //     // }

    //     //DEPRECATED: DFS will pick *any* path, we need at minimum the shortest!
    //     // // Call the recursive DFS function to find the path
    //     // if (this.<R>dfs(sourceState, targetState, visited, path)) {
    //     //     // If a valid path exists, return it
    //     //     return path;
    //     // } else {
    //     //     // If no valid path exists, return an empty list
    //     //     return Collections.emptyList();
    //     // }

    //     //fetch only direct children
    //     // Get the current state's definition
    //     FsmState<S, E, C, I> stateDefinition = states.get(sourceState);
    //     if (stateDefinition == null) {
    //         return path;
    //     }

    //     Optional<Transition<S, E, C, I>> transition = stateDefinition
    //         .getTransactions()
    //         .stream()
    //         .filter(t -> targetState == t.getNextState())
    //         .findFirst();

    //     if (transition.isPresent()) {
    //         path.addLast(transition.get().getNextState());
    //     }

    //     return path;
    // }

    private <R> Optional<R> execute(@NotNull Transition<S, E, C, I> transition, @Nullable I input) {
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

    // /**
    //  * Depth-First Search (DFS) function to find a path between two states in the state machine.
    //  * <p>
    //  * This function explores the state machine's transitions in a depth-first manner to find a path
    //  * from the current state to the target state.
    //  *
    //  * @param currentState The current state being explored.
    //  * @param targetState  The target state to reach.
    //  * @param visited      A set to keep track of visited states during the search.
    //  * @param path         A linked list to record the current path being explored.
    //  * @return True if a valid path is found from the current state to the target state, otherwise
    //  * false.
    //  */
    // @Deprecated(forRemoval = true)
    // private <R> boolean dfs(S currentState, S targetState, Set<S> visited, LinkedList<S> path) {
    //     // Mark the current state as visited and add it to the path
    //     visited.add(currentState);
    //     path.addLast(currentState);

    //     // If the current state is the target state, a valid path is found
    //     if (currentState.equals(targetState)) {
    //         return true;
    //     }

    //     // Get the current state's definition
    //     FsmState<S, E, C, I> stateDefinition = states.get(currentState);

    //     if (stateDefinition != null) {
    //         // Iterate over the transitions from the current state
    //         for (Transition<S, E, C, I> transition : stateDefinition.getTransactions()) {
    //             // Check if the next state in the transition is unvisited
    //             if (!visited.contains(transition.getNextState())) {
    //                 // Recursively search for a path from the next state to the target state
    //                 if (this.<R>dfs(transition.getNextState(), targetState, visited, path)) {
    //                     return true; // A valid path is found
    //                 }
    //             }
    //         }
    //     }

    //     // If no valid path is found from the current state, backtrack
    //     path.removeLast();
    //     return false;
    // }

    /**
     * Attempt to acquire a lock with a timeout.
     *
     * @return An {@code Optional<Boolean>} representing the lock acquisition result. If the lock is
     * acquired successfully, it contains {@code true}; otherwise, it contains
     * {@code false}.
     */
    private Optional<Boolean> acquireLock() {
        try {
            boolean lockAcquired = stateLock.tryLock(10L, TimeUnit.MINUTES);
            return Optional.of(lockAcquired); // Return Optional with lock acquisition result.
        } catch (InterruptedException e) {
            log.error(e.getMessage());
            return Optional.empty(); // Return Optional.empty() in case of an interruption or
            // exception.
        }
    }

    //factory
    @FunctionalInterface
    public static interface Factory<S, E, C, I> {
        Fsm<S, E, C, I> create(S initialState, C context);
    }

    // Builder
    public static class Builder<S, E, C, I> {

        private final S currentState;

        @Getter
        private final Map<S, FsmState<S, E, C, I>> states;

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

        public Builder<S, E, C, I> withState(S state, FsmState<S, E, C, I> stateDefinition) {
            states.put(state, stateDefinition);
            return this;
        }

        public Fsm<S, E, C, I> build() {
            Fsm<S, E, C, I> stateMachine = new Fsm<>(currentState, initialContext);
            stateMachine.states = Collections.unmodifiableMap(states);

            return stateMachine;
        }
    }
}
