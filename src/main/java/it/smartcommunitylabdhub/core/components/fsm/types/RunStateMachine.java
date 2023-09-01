/**
 * RunStateMachine.java
 *
 * This class is responsible for creating and configuring the StateMachine for managing the state
 * transitions of a Run. It defines the states, events, and transitions specific to the Run entity.
 */

package it.smartcommunitylabdhub.core.components.fsm.types;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import it.smartcommunitylabdhub.core.components.fsm.State;
import it.smartcommunitylabdhub.core.components.fsm.StateMachine;
import it.smartcommunitylabdhub.core.components.fsm.Transaction;
import it.smartcommunitylabdhub.core.components.fsm.enums.RunEvent;
import it.smartcommunitylabdhub.core.components.fsm.enums.RunState;
import it.smartcommunitylabdhub.core.models.dtos.RunDTO;
import it.smartcommunitylabdhub.core.services.interfaces.RunService;
import lombok.extern.log4j.Log4j2;

@Component
@Log4j2
public class RunStateMachine {

        @Autowired
        RunService runService;

        /**
         * Create and configure the StateMachine for managing the state transitions of a Run.
         *
         * @param initialState The initial state for the StateMachine.
         * @param initialContext The initial context for the StateMachine.
         * @return The configured StateMachine instance.
         */
        public StateMachine<RunState, RunEvent, Map<String, Object>> create(RunState initialState,
                        Map<String, Object> initialContext) {

                // Create a new StateMachine builder with the initial state and context
                StateMachine.Builder<RunState, RunEvent, Map<String, Object>> builder =
                                new StateMachine.Builder<>(
                                                initialState, initialContext);

                // Define states and transitions
                State<RunState, RunEvent, Map<String, Object>> createState = new State<>();
                State<RunState, RunEvent, Map<String, Object>> readyState = new State<>();
                State<RunState, RunEvent, Map<String, Object>> runningState = new State<>();
                State<RunState, RunEvent, Map<String, Object>> completedState = new State<>();
                State<RunState, RunEvent, Map<String, Object>> errorState = new State<>();

                createState.addTransaction(
                                new Transaction<>(RunEvent.BUILD, RunState.READY,
                                                (input, context) -> true, false));

                readyState.addTransaction(
                                new Transaction<>(RunEvent.RUNNING, RunState.RUNNING,
                                                (input, context) -> true, false));
                readyState.addTransaction(new Transaction<>(RunEvent.COMPLETED, RunState.COMPLETED,
                                (input, context) -> true, false));
                runningState.addTransaction(
                                new Transaction<>(RunEvent.COMPLETED, RunState.COMPLETED,
                                                (input, context) -> true, false));

                // Configure the StateMachine with the defined states and transitions
                builder.withState(RunState.CREATED, createState)
                                .withExitAction(RunState.CREATED, (context) -> {
                                        // update run state
                                        RunDTO runDTO = runService
                                                        .getRun(context.get("runId").toString());
                                        runDTO.setState(RunState.READY.toString());
                                        runService.updateRun(runDTO, runDTO.getId());
                                })
                                .withState(RunState.READY, readyState)
                                .withState(RunState.RUNNING, runningState)
                                .withState(RunState.COMPLETED, completedState)
                                .withErrorState(RunState.ERROR, errorState)
                                .withEntryAction(RunState.ERROR, (context) -> {
                                        RunDTO runDTO = runService
                                                        .getRun(context.get("runId").toString());
                                        runDTO.setState(RunState.ERROR.toString());
                                        runService.updateRun(runDTO, runDTO.getId());
                                })
                                .withStateChangeListener((newState, context) -> log
                                                .info("State Change Listener: " + newState
                                                                + ", context: " + context));

                // Build and return the configured StateMachine instance
                return builder.build();
        }
}
