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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Fsm<S, E, C> {

    private final ReentrantLock stateLock = new ReentrantLock();

    @Getter
    @Setter
    private String uuid;

    private S currentState;
    private S errorState;

    @Getter
    private ConcurrentHashMap<S, FsmState<S, E, C>> states;

    @Getter
    @Setter
    private ConcurrentHashMap<E, BiConsumer<Optional<C>, Optional<C>>> eventListeners;

    @Getter
    @Setter
    private BiConsumer<S, Optional<C>> stateChangeListener;

    private Context<C> context;

    /**
     * Default constructor to create an empty StateMachine.
     */
    public Fsm() {}

    /**
     * Constructor to create a StateMachine with the initial state and context.
     *
     * @param initialState   The initial state of the StateMachine.
     * @param initialContext The initial context for the StateMachine.
     */
    public Fsm(S initialState, Context<C> initialContext) {
        this.uuid = UUID.randomUUID().toString();
        this.currentState = initialState;
        this.context = initialContext;
        this.errorState = null;
        this.states = new ConcurrentHashMap<>();
        this.eventListeners = new ConcurrentHashMap<>();
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

    /**
     * Transition the state machine to a target state based on the provided input. This method executes
     * the necessary actions and guards associated with the transition from the current state to the
     * target state. It follows the path of valid transitions from the current state to the target state
     * and performs the following steps:
     * 1. Checks if a valid path exists from the current state to the target state.
     * 2. Executes the exit action of each intermediate state along the path.
     * 3. Executes the entry action of the target state.
     * 4. Applies the internal logic associated with the target state.
     * <p>
     * If no valid path exists, the state machine transitions to the error state.
     *
     * @param targetState The state to transition to.
     * @param input       The input associated with the transition.
     * @param <T>         The type of the input.
     */
    public <T> void goToState(S targetState, Optional<C> input) {
        acquireLock()
            .ifPresent(lockAcquired -> {
                if (lockAcquired) {
                    try {
                        // Check if a valid path exists from the current state to the target state
                        List<S> path = findPath(currentState, targetState);
                        if (path.isEmpty()) {
                            // No valid path exists; transition to the error state
                            goToErrorState();
                        }

                        // Follow the path
                        // 1. apply internal logic
                        // 2. execute exit action
                        // 3. execute entry action of the current state.
                        for (int i = 0; i < path.size() - 1; i++) {
                            // Get state definition
                            S stateInPath = path.get(i);
                            FsmState<S, E, C> stateDefinition = states.get(stateInPath);

                            // execute exit action
                            Optional
                                .ofNullable(stateDefinition.getExitAction())
                                .ifPresent(exitAction -> exitAction.accept(context.getValue()));

                            // Get next state if exist and execute logic
                            Optional
                                .ofNullable(path.get(i + 1))
                                .ifPresent(nextState -> {
                                    // Retrieve the transition event dynamically
                                    Optional<E> transitionEvent = stateDefinition.getTransitionEvent(nextState);

                                    // Check if a transition event exists and if guard condition is satisfied
                                    if (transitionEvent.isPresent()) {
                                        Transaction<S, E, C> transaction = stateDefinition
                                            .getTransactions()
                                            .get(transitionEvent.get());
                                        if (transaction.getGuard().test(context.getValue(), input)) {
                                            // Notify event listeners for the transition event
                                            transitionEvent.ifPresent(eventName ->
                                                notifyEventListeners(eventName, input)
                                            );

                                    // Update the current state and notify state change listener
                                    currentState = nextState;

                                            // Notify listener for state changed
                                            notifyStateChangeListener(currentState);

                                            // Retrieve the current state definition
                                            FsmState<S, E, C> currentStateDefinition = states.get(currentState);

                                            // Execute entry action
                                            Optional
                                                .ofNullable(currentStateDefinition.getEntryAction())
                                                .ifPresent(entryAction -> entryAction.accept(context.getValue()));

                                            // Apply internal logic of the target state
                                            currentStateDefinition
                                                .getInternalLogic()
                                                .flatMap(internalFunc -> applyInternalFunc(internalFunc, input));
                                        } else {
                                            // Guard condition not satisfied, skip this transition
                                        }
                                    }
                                });
                        }
                    } finally {
                        stateLock.unlock();
                    }
                }
            });
    }

    /**
     * Transition the state machine to the error state. This method is invoked when there's an error
     * or an invalid state transition, and it handles the transition to the specified error state.
     */
    private <T> void goToErrorState() {
        // Check if an error state is defined
        if (errorState != null) {
            // Set the current state to the error state
            currentState = errorState;
            FsmState<S, E, C> errorStateDefinition = states.get(errorState);
            if (errorStateDefinition != null) {
                // Execute error logic if defined for the error state
                errorStateDefinition
                    .getInternalLogic()
                    .flatMap(internalFunc -> applyInternalFunc(internalFunc, Optional.empty()));
            } else {
                // Throw an exception if the error state is not defined
                throw new IllegalStateException("Invalid error state: " + errorState + " : " + this.getUuid());
            }
        } else {
            // Throw an exception if the error state is not set
            throw new IllegalStateException("Error state not set" + " : " + this.getUuid());
        }
    }

    /**
     * Add a single event listener to the map if not present
     *
     * @param event
     * @param listener
     */
    public void setEventListener(E event, BiConsumer<Optional<C>, Optional<C>> listener) {
        eventListeners.putIfAbsent(event, listener);
    }

    public FsmState<S, E, C> getState(S state) {
        return states.get(state);
    }

    /**
     * Retrieve the context of the current state.
     *
     * @return An optional containing the context of the current state, or empty if no context is
     * set.
     */
    public Optional<C> getStateMachineContext() {
        // Lock access to currentState to ensure thread safety
        stateLock.lock();
        try {
            FsmState<S, E, C> currentStateDefinition = states.get(currentState);
            if (currentStateDefinition != null) {
                return context.getValue();
            } else {
                return Optional.empty();
            }
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Find a path from the source state to the target state in the state machine.
     * <p>
     * This method initiates a depth-first search (DFS) to explore the state machine's transitions
     * and find a valid path from the source state to the target state.
     *
     * @param sourceState The starting state of the path.
     * @param targetState The state to reach.
     * @return A list of states representing a valid path from the source state to the target state,
     * or an empty list if no valid path is found.
     */
    private List<S> findPath(S sourceState, S targetState) {
        Set<S> visited = new HashSet<>();
        LinkedList<S> path = new LinkedList<>();

        // Call the recursive DFS function to find the path
        if (dfs(sourceState, targetState, visited, path)) {
            // If a valid path exists, return it
            return path;
        } else {
            // If no valid path exists, return an empty list
            return Collections.emptyList();
        }
    }

    /**
     * Depth-First Search (DFS) function to find a path between two states in the state machine.
     * <p>
     * This function explores the state machine's transitions in a depth-first manner to find a path
     * from the current state to the target state.
     *
     * @param currentState The current state being explored.
     * @param targetState  The target state to reach.
     * @param visited      A set to keep track of visited states during the search.
     * @param path         A linked list to record the current path being explored.
     * @return True if a valid path is found from the current state to the target state, otherwise
     * false.
     */
    private boolean dfs(S currentState, S targetState, Set<S> visited, LinkedList<S> path) {
        // Mark the current state as visited and add it to the path
        visited.add(currentState);
        path.addLast(currentState);

        // If the current state is the target state, a valid path is found
        if (currentState.equals(targetState)) {
            return true;
        }

        // Get the current state's definition
        FsmState<S, E, C> stateDefinition = states.get(currentState);

        // Iterate over the transitions from the current state
        for (E event : stateDefinition.getTransactions().keySet()) {
            Transaction<S, E, C> transaction = stateDefinition.getTransactions().get(event);

            // Check if the next state in the transaction is unvisited
            if (!visited.contains(transaction.getNextState())) {
                // Recursively search for a path from the next state to the target state
                if (dfs(transaction.getNextState(), targetState, visited, path)) {
                    return true; // A valid path is found
                }
            }
        }

        // If no valid path is found from the current state, backtrack
        path.removeLast();
        return false;
    }

    /**
     * Applies the internal logic associated with a state, allowing for customized handling of state
     * transitions and updates to the state machine's context.
     *
     * @param stateLogic The state logic implementation to apply.
     * @param <T>        The type of result returned by the state logic.
     * @return An optional result obtained from applying the internal logic, or empty if not
     * applicable.
     */
    private <T> Optional<T> applyInternalFunc(StateLogic<S, E, C, T> stateLogic, Optional<C> input) {
        return stateLogic.applyLogic(context.getValue(), input, this);
    }

    /**
     * Notifies the state change listener, if registered, about a state transition.
     *
     * @param newState The new state to which the state machine has transitioned.
     */
    private void notifyStateChangeListener(S newState) {
        if (stateChangeListener != null) {
            stateChangeListener.accept(newState, context.getValue());
        }
    }

    /**
     * Notifies event listeners, if registered, about an event associated with a state.
     *
     * @param eventName The event name that occurred.
     * @param input     The input associated with the event.
     */
    private <T> void notifyEventListeners(E eventName, Optional<C> input) {
        BiConsumer<Optional<C>, Optional<C>> listener = eventListeners.get(eventName);
        if (listener != null) {
            listener.accept(context.getValue(), input);
        }
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
            boolean lockAcquired = stateLock.tryLock(10L, TimeUnit.MINUTES);
            return Optional.of(lockAcquired); // Return Optional with lock acquisition result.
        } catch (InterruptedException e) {
            log.error(e.getMessage());
            return Optional.empty(); // Return Optional.empty() in case of an interruption or
            // exception.
        }
    }

    // Builder
    public static class Builder<S, E, C> {

        private final S currentState;
        private final ConcurrentHashMap<S, FsmState<S, E, C>> states;
        private final ConcurrentHashMap<E, BiConsumer<Optional<C>, Optional<C>>> eventListeners;
        private final Context<C> initialContext;
        private S errorState;
        private BiConsumer<S, Optional<C>> stateChangeListener;

        public Builder(S initialState, C initialContext) {
            this.currentState = initialState;
            this.initialContext = new Context<>(Optional.ofNullable(initialContext));
            this.states = new ConcurrentHashMap<>();
            this.eventListeners = new ConcurrentHashMap<>();
        }

        /**
         * Adds a state and its definition to the builder's configuration.
         *
         * @param state           The state to add.
         * @param stateDefinition The definition of the state.
         * @return This builder instance of the state, allowing for method chaining.
         */

        public FsmState.StateBuilder<S, E, C> withState(S state, FsmState<S, E, C> stateDefinition) {
            states.put(state, stateDefinition);
            return new FsmState.StateBuilder<>(state, this, stateDefinition);
        }

        /**
         * Sets the error state and its definition in the builder's configuration. If the error
         * state doesn't exist in the states map, it will be added.
         *
         * @param errorState      The error state to set.
         * @param stateDefinition The definition of the error state.
         * @return This builder instance, allowing for method chaining.
         */
        public Builder<S, E, C> withErrorState(S errorState, FsmState<S, E, C> stateDefinition) {
            this.errorState = errorState;

            // Add the error state to the states map if it doesn't exist
            states.putIfAbsent(errorState, stateDefinition);
            return this;
        }

        /**
         * Adds an event listener to the builder's configuration.
         *
         * @param eventName The name of the event to listen for.
         * @param listener  The listener to handle the event.
         * @return This builder instance, allowing for method chaining.
         */
        public <T> Builder<S, E, C> withEventListener(E eventName, BiConsumer<Optional<C>, Optional<C>> listener) {
            eventListeners.put(eventName, listener);
            return this;
        }

        /**
         * Sets the state change listener for the builder's configuration.
         *
         * @param listener The listener to be notified when the state changes.
         * @return This builder instance, allowing for method chaining.
         */
        public Builder<S, E, C> withStateChangeListener(BiConsumer<S, Optional<C>> listener) {
            stateChangeListener = listener;
            return this;
        }

        public Fsm<S, E, C> build() {
            Fsm<S, E, C> stateMachine = new Fsm<>(currentState, initialContext);
            stateMachine.states = states;
            stateMachine.errorState = errorState;
            stateMachine.eventListeners = eventListeners;
            stateMachine.stateChangeListener = stateChangeListener;

            return stateMachine;
        }
    }
}
