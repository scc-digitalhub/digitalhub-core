package it.smartcommunitylabdhub.core.components.run;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.events.RunnableChangedEvent;
import it.smartcommunitylabdhub.commons.events.RunnableMonitorObject;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.infrastructure.Runtime;
import it.smartcommunitylabdhub.commons.models.entities.function.Function;
import it.smartcommunitylabdhub.commons.models.entities.function.FunctionBaseSpec;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.models.entities.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.entities.run.RunBaseStatus;
import it.smartcommunitylabdhub.commons.models.entities.task.Task;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.utils.RunUtils;
import it.smartcommunitylabdhub.commons.models.utils.TaskUtils;
import it.smartcommunitylabdhub.commons.services.entities.RunService;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.core.components.infrastructure.factories.runtimes.RuntimeFactory;
import it.smartcommunitylabdhub.core.models.entities.function.FunctionEntity;
import it.smartcommunitylabdhub.core.models.entities.run.RunEntity;
import it.smartcommunitylabdhub.core.models.entities.task.TaskEntity;
import it.smartcommunitylabdhub.core.models.queries.specifications.CommonSpecification;
import it.smartcommunitylabdhub.core.services.EntityService;
import it.smartcommunitylabdhub.fsm.Fsm;
import it.smartcommunitylabdhub.fsm.enums.RunEvent;
import it.smartcommunitylabdhub.fsm.exceptions.InvalidTransactionException;
import it.smartcommunitylabdhub.fsm.types.RunStateMachineFactory;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RunManager {

    @Autowired
    private RunStateMachineFactory runStateMachine;

    @Autowired
    private EntityService<Run, RunEntity> entityService;

    @Autowired
    private EntityService<Task, TaskEntity> taskEntityService;

    @Autowired
    private EntityService<Function, FunctionEntity> functionEntityService;

    @Autowired
    private RunService runService;

    @Autowired
    private RuntimeFactory runtimeFactory;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public Run build(@NotNull Run run) throws NoSuchEntityException {
        // GET state machine, init state machine with status
        RunBaseSpec runBaseSpec = new RunBaseSpec();
        runBaseSpec.configure(run.getSpec());
        RunSpecAccessor runSpecAccessor = RunUtils.parseTask(runBaseSpec.getTask());

        // Retrieve Function
        String functionId = runSpecAccessor.getVersion();
        Function function = functionEntityService.get(functionId);

        // Retrieve Task
        Specification<TaskEntity> where = Specification.allOf(
            CommonSpecification.projectEquals(function.getProject()),
            createFunctionSpecification(TaskUtils.buildFunctionString(function)),
            createTaskKindSpecification(runSpecAccessor.getTask())
        );
        Task task = taskEntityService.searchAll(where).stream().findFirst().orElse(null);

        // Retrieve state machine
        Fsm<State, RunEvent, Map<String, Serializable>> fsm = createFsm(run);

        // Add Internal logic to be executed when state change from CREATED to READY
        fsm
            .getState(State.CREATED)
            .getTransaction(RunEvent.BUILD)
            .setInternalLogic((context, input, fsmInstance) -> {
                if (!Optional.ofNullable(runBaseSpec.getLocalExecution()).orElse(Boolean.FALSE)) {
                    // Retrieve Runtime and build run
                    Runtime<
                        ? extends FunctionBaseSpec,
                        ? extends RunBaseSpec,
                        ? extends RunBaseStatus,
                        ? extends RunRunnable
                    > runtime = runtimeFactory.getRuntime(function.getKind());

                    // Build RunSpec using Runtime now if wrong type is passed to a specific runtime
                    // an exception occur! for.
                    RunBaseSpec runSpecBuilt = runtime.build(function, task, run);

                    return Optional.of(runSpecBuilt);
                }
                return Optional.empty();
            });

        try {
            // Update run state to BUILT
            Optional<RunBaseSpec> runSpecBuilt = fsm.goToState(State.BUILT, null);
            runSpecBuilt.ifPresent(spec -> {
                // Update run spec
                run.setSpec(spec.toMap());

                // Update run state to BUILT
                run.getStatus().put("state", State.BUILT.toString());

                if (log.isTraceEnabled()) {
                    log.trace("Built run: {}", run);
                }
            });

            entityService.update(run.getId(), run);
            return run;
        } catch (InvalidTransactionException e) {
            // log error
            log.error("Invalid transaction from state {}  to state {}", e.getFromState(), e.getToState());
            throw new InvalidTransactionException(e.getFromState(), e.getToState());
        }
    }

    public Run run(@NotNull Run run) throws NoSuchEntityException, InvalidTransactionException {
        // GET state machine, init state machine with status
        RunBaseSpec runBaseSpec = new RunBaseSpec();
        runBaseSpec.configure(run.getSpec());
        RunSpecAccessor runSpecAccessor = RunUtils.parseTask(runBaseSpec.getTask());

        // Retrieve Function
        String functionId = runSpecAccessor.getVersion();
        Function function = functionEntityService.get(functionId);

        // Retrieve state machine
        Fsm<State, RunEvent, Map<String, Serializable>> fsm = createFsm(run);

        fsm
            .getState(State.BUILT)
            .getTransaction(RunEvent.RUN)
            .setInternalLogic((context, input, stateMachine) -> {
                if (!Optional.ofNullable(runBaseSpec.getLocalExecution()).orElse(Boolean.FALSE)) {
                    // Retrieve Runtime and build run
                    Runtime<
                        ? extends FunctionBaseSpec,
                        ? extends RunBaseSpec,
                        ? extends RunBaseStatus,
                        ? extends RunRunnable
                    > runtime = runtimeFactory.getRuntime(function.getKind());
                    // Create Runnable
                    RunRunnable runnable = runtime.run(run);

                    return Optional.of(runnable);
                } else {
                    return Optional.empty();
                }
            });

        try {
            Optional<RunRunnable> runnable = fsm.goToState(State.READY, null);
            runnable.ifPresent(r -> {
                // Dispatch Runnable event to specific event listener es (serve,job,deploy...)
                eventPublisher.publishEvent(r);

                // Update run state to READY
                run.getStatus().put("state", State.READY.toString());
            });
            entityService.update(run.getId(), run);

            return run;
        } catch (InvalidTransactionException e) {
            // log error
            log.error("Invalid transaction from state {}  to state {}", e.getFromState(), e.getToState());
            throw new InvalidTransactionException(e.getFromState(), e.getToState());
        }
    }

    public Run stop(@NotNull Run run) {
        // GET state machine, init state machine with status
        RunBaseSpec runBaseSpec = new RunBaseSpec();
        runBaseSpec.configure(run.getSpec());
        RunSpecAccessor runSpecAccessor = RunUtils.parseTask(runBaseSpec.getTask());

        // Retrieve Function
        String functionId = runSpecAccessor.getVersion();
        Function function = functionEntityService.get(functionId);

        // Retrieve state machine
        Fsm<State, RunEvent, Map<String, Serializable>> fsm = createFsm(run);

        fsm
            .getState(State.RUNNING)
            .getTransaction(RunEvent.STOP)
            .setInternalLogic((context, input, stateMachine) -> {
                if (!Optional.ofNullable(runBaseSpec.getLocalExecution()).orElse(Boolean.FALSE)) {
                    // Retrieve Runtime and build run
                    Runtime<
                        ? extends FunctionBaseSpec,
                        ? extends RunBaseSpec,
                        ? extends RunBaseStatus,
                        ? extends RunRunnable
                    > runtime = runtimeFactory.getRuntime(function.getKind());
                    // Create Runnable
                    RunRunnable runnable = runtime.stop(run);

                    return Optional.of(runnable);
                } else {
                    return Optional.empty();
                }
            });

        try {
            Optional<RunRunnable> runnable = fsm.goToState(State.STOP, null);
            runnable.ifPresent(r -> {
                // Dispatch Runnable event to specific event listener es (serve,job,deploy...)
                eventPublisher.publishEvent(r);

                // Update run state to READY
                run.getStatus().put("state", State.STOP.toString());
            });
            entityService.update(run.getId(), run);

            return run;
        } catch (InvalidTransactionException e) {
            // log error
            log.error("Invalid transaction from state {}  to state {}", State.RUNNING, State.STOPPED);
            throw new InvalidTransactionException(State.RUNNING.toString(), State.STOPPED.toString());
        }
    }

    public Run delete(@NotNull Run run) {
        // GET state machine, init state machine with status
        RunBaseSpec runBaseSpec = new RunBaseSpec();
        runBaseSpec.configure(run.getSpec());
        RunSpecAccessor runSpecAccessor = RunUtils.parseTask(runBaseSpec.getTask());

        // Retrieve Function
        String functionId = runSpecAccessor.getVersion();
        Function function = functionEntityService.get(functionId);

        // Retrieve state machine
        Fsm<State, RunEvent, Map<String, Serializable>> fsm = createFsm(run);

        fsm
            .getState(State.RUNNING)
            .getTransaction(RunEvent.DELETING)
            .setInternalLogic((context, input, stateMachine) -> {
                if (!Optional.ofNullable(runBaseSpec.getLocalExecution()).orElse(Boolean.FALSE)) {
                    // Retrieve Runtime and build run
                    Runtime<
                        ? extends FunctionBaseSpec,
                        ? extends RunBaseSpec,
                        ? extends RunBaseStatus,
                        ? extends RunRunnable
                    > runtime = runtimeFactory.getRuntime(function.getKind());
                    // Create Runnable
                    RunRunnable runnable = runtime.delete(run);

                    return Optional.of(runnable);
                } else {
                    return Optional.empty();
                }
            });
        fsm
            .getState(State.STOPPED)
            .getTransaction(RunEvent.DELETING)
            .setInternalLogic((context, input, stateMachine) -> {
                if (!Optional.ofNullable(runBaseSpec.getLocalExecution()).orElse(Boolean.FALSE)) {
                    // Retrieve Runtime and build run
                    Runtime<
                        ? extends FunctionBaseSpec,
                        ? extends RunBaseSpec,
                        ? extends RunBaseStatus,
                        ? extends RunRunnable
                    > runtime = runtimeFactory.getRuntime(function.getKind());
                    // Create Runnable
                    RunRunnable runnable = runtime.delete(run);

                    return Optional.of(runnable);
                } else {
                    return Optional.empty();
                }
            });

        try {
            Optional<RunRunnable> runnable = fsm.goToState(State.DELETING, null);
            // Dispatch Runnable event to specific event listener es (serve,job,deploy...)
            runnable.ifPresent(eventPublisher::publishEvent);

            //update
            run.getStatus().put("state", State.DELETING.toString());
            entityService.update(run.getId(), run);

            //if runnable we are deleting in async, otherwise move to delete
            if (runnable.isEmpty()) {
                //dispatch event
                onDeleted(run, null);
            }

            return run;
        } catch (InvalidTransactionException e) {
            // log error
            log.error("Invalid transaction from state {}  to state {}", e.getFromState(), e.getToState());
            throw new InvalidTransactionException(e.getFromState(), e.getToState());
        }
    }

    @Async
    @EventListener
    public void onChangedEvent(RunnableChangedEvent<RunRunnable> event) {
        if (event.getRunMonitorObject() == null) {
            return;
        }

        // Retrieve the RunMonitorObject from the event
        RunnableMonitorObject runnableMonitorObject = event.getRunMonitorObject();

        // Use service to retrieve the run and check if state is changed
        Optional
            .of(entityService.find(runnableMonitorObject.getRunId()))
            .ifPresentOrElse(
                run -> {
                    if (
                        !Objects.equals(
                            StatusFieldAccessor.with(run.getStatus()).getState(),
                            runnableMonitorObject.getStateId()
                        )
                    ) {
                        switch (State.valueOf(runnableMonitorObject.getStateId())) {
                            case COMPLETED:
                                onCompleted(run, event);
                                break;
                            case ERROR:
                                onError(run, event);
                                break;
                            case RUNNING:
                                onRunning(run, event);
                                break;
                            case STOPPED:
                                onStopped(run, event);
                                break;
                            case DELETED:
                                onDeleted(run, event);
                                break;
                            default:
                                log.info(
                                    "State {} for run id {} not managed",
                                    runnableMonitorObject.getStateId(),
                                    runnableMonitorObject.getRunId()
                                );
                                break;
                        }
                    } else {
                        log.info(
                            "State {} for run id {} not changed",
                            runnableMonitorObject.getStateId(),
                            runnableMonitorObject.getRunId()
                        );
                    }
                },
                () -> {
                    log.error("Run with id {} not found", runnableMonitorObject.getRunId());
                }
            );
    }

    // Callback Methods
    private void onRunning(Run run, RunnableChangedEvent<RunRunnable> event) {
        // Try to move forward state machine based on current state
        Fsm<State, RunEvent, Map<String, Serializable>> fsm = createFsm(run);

        Function function = retrieveFunction(run);

        // Retrieve Runtime
        Runtime<
            ? extends FunctionBaseSpec,
            ? extends RunBaseSpec,
            ? extends RunBaseStatus,
            ? extends RunRunnable
        > runtime = runtimeFactory.getRuntime(function.getKind());

        // Define logic for state READY
        fsm
            .getState(State.READY)
            .getTransaction(RunEvent.EXECUTE)
            .setInternalLogic((context, input, fsmInstance) -> {
                log.info(
                    "Executing internal logic for state RUNNING, " + "event :{}, context: {}, input: {}",
                    RunEvent.EXECUTE,
                    context,
                    input
                );

                RunRunnable runnable = event != null ? event.getRunnable() : null;
                RunBaseStatus runStatus = runtime.onRunning(run, runnable);
                return Optional.ofNullable(runStatus);
            });

        try {
            Optional<RunBaseStatus> runStatus = fsm.goToState(State.RUNNING, null);
            runStatus.ifPresentOrElse(
                runBaseStatus -> {
                    run.setStatus(
                        MapUtils.mergeMultipleMaps(runBaseStatus.toMap(), Map.of("state", State.RUNNING.toString()))
                    );
                },
                () -> {
                    // Update run state to RUNNING
                    run.getStatus().put("state", State.RUNNING.toString());
                }
            );
            entityService.update(run.getId(), run);
        } catch (InvalidTransactionException e) {
            log.error("Invalid transaction from state {}  to state {}", State.READY, State.RUNNING);
        }
    }

    /**
     * A callback method to handle complete in the run, update run status and execute internal logic for the COMPLETE state.
     *
     * @param event the RunChangedEvent triggering the error
     * @throws NoSuchEntityException if the entity being accessed does not exist
     */
    private void onCompleted(Run run, RunnableChangedEvent<RunRunnable> event) {
        // Try to move forward state machine based on current state
        Fsm<State, RunEvent, Map<String, Serializable>> fsm = createFsm(run);

        Function function = retrieveFunction(run);

        // Retrieve Runtime
        Runtime<
            ? extends FunctionBaseSpec,
            ? extends RunBaseSpec,
            ? extends RunBaseStatus,
            ? extends RunRunnable
        > runtime = runtimeFactory.getRuntime(function.getKind());

        // Define logic for state RUNNING
        fsm
            .getState(State.RUNNING)
            .getTransaction(RunEvent.COMPLETE)
            .setInternalLogic((context, input, fsmInstance) -> {
                log.info(
                    "Executing internal logic for state RUNNING, " + "event :{}, context: {}, input: {}",
                    RunEvent.COMPLETE,
                    context,
                    input
                );

                RunRunnable runnable = event != null ? event.getRunnable() : null;
                RunBaseStatus runStatus = runtime.onComplete(run, runnable);
                return Optional.ofNullable(runStatus);
            });

        try {
            Optional<RunBaseStatus> runStatus = fsm.goToState(State.COMPLETED, null);
            runStatus.ifPresentOrElse(
                runBaseStatus -> {
                    run.setStatus(
                        MapUtils.mergeMultipleMaps(runBaseStatus.toMap(), Map.of("state", State.COMPLETED.toString()))
                    );
                },
                () -> {
                    // Update run state to RUNNING
                    run.getStatus().put("state", State.COMPLETED.toString());
                }
            );

            //update
            entityService.update(run.getId(), run);
        } catch (InvalidTransactionException e) {
            log.error("Invalid transaction from state {}  to state {}", e.getFromState(), e.getToState());
            throw new InvalidTransactionException(e.getFromState(), e.getToState());
        }
    }

    /**
     * A callback method to handle stop in the run, update run status and execute internal logic for the STOP state.
     *
     * @param event the RunChangedEvent triggering the stop
     * @throws NoSuchEntityException if the entity being accessed does not exist
     */
    private void onStopped(Run run, RunnableChangedEvent<RunRunnable> event) {
        // Try to move forward state machine based on current state
        Fsm<State, RunEvent, Map<String, Serializable>> fsm = createFsm(run);

        Function function = retrieveFunction(run);

        // Retrieve Runtime
        Runtime<
            ? extends FunctionBaseSpec,
            ? extends RunBaseSpec,
            ? extends RunBaseStatus,
            ? extends RunRunnable
        > runtime = runtimeFactory.getRuntime(function.getKind());

        // Define logic for state STOP
        fsm
            .getState(State.STOP)
            .getTransaction(RunEvent.STOP)
            .setInternalLogic((context, input, fsmInstance) -> {
                log.info(
                    "Executing internal logic for state STOP, " + "event :{}, context: {}, input: {}",
                    RunEvent.STOP,
                    context,
                    input
                );

                RunRunnable runnable = event != null ? event.getRunnable() : null;
                RunBaseStatus runStatus = runtime.onStopped(run, runnable);
                return Optional.ofNullable(runStatus);
            });

        try {
            Optional<RunBaseStatus> runStatus = fsm.goToState(State.STOPPED, null);
            runStatus.ifPresentOrElse(
                runBaseStatus -> {
                    run.setStatus(
                        MapUtils.mergeMultipleMaps(runBaseStatus.toMap(), Map.of("state", State.STOPPED.toString()))
                    );
                },
                () -> {
                    // Update run state to STOPPED
                    run.getStatus().put("state", State.STOPPED.toString());
                }
            );

            //update
            entityService.update(run.getId(), run);
        } catch (InvalidTransactionException e) {
            log.error("Invalid transaction from state {}  to state {}", e.getFromState(), e.getToState());
            throw new InvalidTransactionException(e.getFromState(), e.getToState());
        }
    }

    /**
     * A callback method to handle errors in the run, update run status and execute internal logic for the ERROR state.
     *
     * @param event the RunChangedEvent triggering the error
     * @throws NoSuchEntityException if the entity being accessed does not exist
     */
    private void onError(Run run, RunnableChangedEvent<RunRunnable> event) {
        // Try to move forward state machine based on current state
        Fsm<State, RunEvent, Map<String, Serializable>> fsm = createFsm(run);
        Function function = retrieveFunction(run);

        Runtime<
            ? extends FunctionBaseSpec,
            ? extends RunBaseSpec,
            ? extends RunBaseStatus,
            ? extends RunRunnable
        > runtime = runtimeFactory.getRuntime(function.getKind());

        fsm
            .getState(State.RUNNING)
            .getTransaction(RunEvent.ERROR)
            .setInternalLogic((context, input, fsmInstance) -> {
                log.info(
                    "Executing internal logic for state RUNNING, " + "event :{}, context: {}, input: {}",
                    RunEvent.ERROR,
                    context,
                    input
                );

                RunRunnable runnable = event != null ? event.getRunnable() : null;
                RunBaseStatus runStatus = runtime.onError(run, runnable);
                return Optional.ofNullable(runStatus);
            });

        fsm
            .getState(State.STOP)
            .getTransaction(RunEvent.ERROR)
            .setInternalLogic((context, input, fsmInstance) -> {
                log.info(
                    "Executing internal logic for state STOP, " + "event :{}, context: {}, input: {}",
                    RunEvent.ERROR,
                    context,
                    input
                );
                RunRunnable runnable = event != null ? event.getRunnable() : null;
                RunBaseStatus runStatus = runtime.onError(run, runnable);
                return Optional.ofNullable(runStatus);
            });

        try {
            Optional<RunBaseStatus> runStatus = fsm.goToState(State.ERROR, null);
            runStatus.ifPresentOrElse(
                runBaseStatus -> {
                    run.setStatus(
                        MapUtils.mergeMultipleMaps(runBaseStatus.toMap(), Map.of("state", State.ERROR.toString()))
                    );
                },
                () -> {
                    // Update run state to RUNNING
                    run.getStatus().put("state", State.ERROR.toString());
                }
            );

            entityService.update(run.getId(), run);
        } catch (InvalidTransactionException e) {
            log.error("Invalid transaction from state {} to state {}", e.getFromState(), e.getToState());
        }
    }

    /**
     * A method to handle delete in the run, update run status and execute internal logic for the STOP state.
     *
     * @param event the RunChangedEvent triggering the delete
     * @throws NoSuchEntityException if the entity being accessed does not exist
     */
    private void onDeleted(Run run, RunnableChangedEvent<RunRunnable> event) {
        // Try to move forward state machine based on current state
        Fsm<State, RunEvent, Map<String, Serializable>> fsm = createFsm(run);

        // Retrieve Runtime
        Function function = retrieveFunction(run);
        Runtime<
            ? extends FunctionBaseSpec,
            ? extends RunBaseSpec,
            ? extends RunBaseStatus,
            ? extends RunRunnable
        > runtime = runtimeFactory.getRuntime(function.getKind());

        // Define logic for state DELETING
        fsm
            .getState(State.DELETING)
            .getTransaction(RunEvent.DELETING)
            .setInternalLogic((context, input, fsmInstance) -> {
                log.info(
                    "Executing internal logic for state DELETING, " + "event :{}, context: {}, input: {}",
                    RunEvent.DELETING,
                    context,
                    input
                );

                RunRunnable runnable = event != null ? event.getRunnable() : null;
                RunBaseStatus runStatus = runtime.onDeleted(run, runnable);
                return Optional.ofNullable(runStatus);
            });

        try {
            Optional<RunBaseStatus> runStatus = fsm.goToState(State.DELETED, null);
            runStatus.ifPresentOrElse(
                runBaseStatus -> {
                    run.setStatus(
                        MapUtils.mergeMultipleMaps(runBaseStatus.toMap(), Map.of("state", State.DELETED.toString()))
                    );
                },
                () -> {
                    // Update run state to DELETED
                    run.getStatus().put("state", State.DELETED.toString());
                }
            );

            //update run with DELETED
            //explicit before delete to let external event handlers receive the msg
            entityService.update(run.getId(), run);

            //delete run via service to handle cascade
            runService.deleteRun(run.getId(), Boolean.TRUE);
        } catch (InvalidTransactionException e) {
            log.error("Invalid transaction from state {}  to state {}", e.getFromState(), e.getToState());
            throw new InvalidTransactionException(e.getFromState(), e.getToState());
        }
    }

    /**
     * Creates and returns a finite state machine (FSM) with the specified initial state and context, based on the given run.
     *
     * @param run the run object used to retrieve the entity and initialize the state machine context
     * @return the FSM created and initialized for the given run
     */
    private Fsm<State, RunEvent, Map<String, Serializable>> createFsm(Run run) {
        // Retrieve entity from run dto

        // Create state machine context
        Map<String, Serializable> ctx = new HashMap<>();
        ctx.put("run", run);

        // Initialize state machine
        Fsm<State, RunEvent, Map<String, Serializable>> fsm = runStateMachine.builder(
            State.valueOf(StatusFieldAccessor.with(run.getStatus()).getState()),
            ctx
        );

        // On state change delegate state machine to update the run
        fsm.setStateChangeListener((state, context) -> {
            log.info("State Change Listener: {}, context: {}", state, context);
        });
        return fsm;
    }

    /**
     * Retrieve a function based on the given run.
     *
     * @param run the run to retrieve the function for
     * @return the retrieved function
     */
    private Function retrieveFunction(Run run) throws NoSuchEntityException {
        // GET state machine, init state machine with status
        RunBaseSpec runBaseSpec = new RunBaseSpec();
        runBaseSpec.configure(run.getSpec());
        RunSpecAccessor runSpecAccessor = RunUtils.parseTask(runBaseSpec.getTask());

        // Retrieve Function
        String functionId = runSpecAccessor.getVersion();
        return functionEntityService.get(functionId);
    }

    private Specification<TaskEntity> createFunctionSpecification(String function) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("function"), function);
    }

    private Specification<TaskEntity> createTaskKindSpecification(String kind) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("kind"), kind);
    }
}
