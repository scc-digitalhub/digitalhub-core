package it.smartcommunitylabdhub.core.components.run;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.events.RunnableChangedEvent;
import it.smartcommunitylabdhub.commons.events.RunnableMonitorObject;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.infrastructure.Runtime;
import it.smartcommunitylabdhub.commons.infrastructure.SecuredRunnable;
import it.smartcommunitylabdhub.commons.models.base.Executable;
import it.smartcommunitylabdhub.commons.models.base.ExecutableBaseSpec;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.models.entities.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.entities.run.RunBaseStatus;
import it.smartcommunitylabdhub.commons.models.entities.task.Task;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.utils.RunUtils;
import it.smartcommunitylabdhub.commons.models.utils.TaskUtils;
import it.smartcommunitylabdhub.commons.services.entities.RunService;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.core.components.infrastructure.factories.processors.ProcessorRegistry;
import it.smartcommunitylabdhub.core.components.infrastructure.factories.runtimes.RuntimeFactory;
import it.smartcommunitylabdhub.core.models.entities.RunEntity;
import it.smartcommunitylabdhub.core.models.entities.TaskEntity;
import it.smartcommunitylabdhub.core.models.queries.specifications.CommonSpecification;
import it.smartcommunitylabdhub.core.services.EntityService;
import it.smartcommunitylabdhub.core.services.ExecutableEntityService;
import it.smartcommunitylabdhub.fsm.Fsm;
import it.smartcommunitylabdhub.fsm.enums.RunEvent;
import it.smartcommunitylabdhub.fsm.exceptions.InvalidTransactionException;
import it.smartcommunitylabdhub.fsm.types.RunStateMachineFactory;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private ExecutableEntityService executableEntityServiceProvider;

    @Autowired
    private RunService runService;

    @Autowired
    private RuntimeFactory runtimeFactory;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    ProcessorRegistry processorRegistry;

    public Run build(@NotNull Run run) throws NoSuchEntityException {
        // GET state machine, init state machine with status
        RunBaseSpec runBaseSpec = new RunBaseSpec();
        runBaseSpec.configure(run.getSpec());
        RunSpecAccessor runSpecAccessor = RunUtils.parseTask(runBaseSpec.getTask());

        try {
            // Retrieve Executable
            String executableId = runSpecAccessor.getVersion();
            Executable executable = executableEntityServiceProvider
                .getEntityServiceByRuntime(runSpecAccessor.getRuntime())
                .get(executableId);

            // Retrieve Task
            Specification<TaskEntity> where = Specification.allOf(
                CommonSpecification.projectEquals(executable.getProject()),
                createExecutableSpecification(TaskUtils.buildString(executable)),
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
                            ? extends ExecutableBaseSpec,
                            ? extends RunBaseSpec,
                            ? extends RunBaseStatus,
                            ? extends RunRunnable
                        > runtime = runtimeFactory.getRuntime(executable.getKind());

                        // Build RunSpec using Runtime now if wrong type is passed to a specific runtime
                        // an exception occur! for.
                        RunBaseSpec runSpecBuilt = runtime.build(executable, task, run);

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

                return entityService.update(run.getId(), run);
            } catch (InvalidTransactionException e) {
                // log error
                log.debug("Invalid transaction from state {}  to state {}", e.getFromState(), e.getToState());
                throw e;
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    public Run run(@NotNull Run run) throws NoSuchEntityException, InvalidTransactionException {
        // GET state machine, init state machine with status
        RunBaseSpec runBaseSpec = new RunBaseSpec();
        runBaseSpec.configure(run.getSpec());
        RunSpecAccessor runSpecAccessor = RunUtils.parseTask(runBaseSpec.getTask());

        try {
            // Retrieve Executable
            String executableId = runSpecAccessor.getVersion();
            Executable executable = executableEntityServiceProvider
                .getEntityServiceByRuntime(runSpecAccessor.getRuntime())
                .get(executableId);

            // Retrieve state machine
            Fsm<State, RunEvent, Map<String, Serializable>> fsm = createFsm(run);

            fsm
                .getState(State.BUILT)
                .getTransaction(RunEvent.RUN)
                .setInternalLogic((context, input, stateMachine) -> {
                    if (!Optional.ofNullable(runBaseSpec.getLocalExecution()).orElse(Boolean.FALSE)) {
                        // Retrieve Runtime and build run
                        Runtime<
                            ? extends ExecutableBaseSpec,
                            ? extends RunBaseSpec,
                            ? extends RunBaseStatus,
                            ? extends RunRunnable
                        > runtime = runtimeFactory.getRuntime(executable.getKind());
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
                    //extract auth from security context to inflate secured credentials
                    //TODO refactor properly
                    if (r instanceof SecuredRunnable) {
                        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                        if (auth != null) {
                            ((SecuredRunnable) r).setCredentials(auth);
                        }
                    }

                    // Dispatch Runnable event to specific event listener es (serve,job,deploy...)
                    eventPublisher.publishEvent(r);

                    // Update run state to READY
                    run.getStatus().put("state", State.READY.toString());
                });

                return entityService.update(run.getId(), run);
            } catch (InvalidTransactionException e) {
                // log error
                log.debug("Invalid transaction from state {}  to state {}", e.getFromState(), e.getToState());
                throw e;
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    public Run stop(@NotNull Run run) throws NoSuchEntityException {
        // GET state machine, init state machine with status
        RunBaseSpec runBaseSpec = new RunBaseSpec();
        runBaseSpec.configure(run.getSpec());
        RunSpecAccessor runSpecAccessor = RunUtils.parseTask(runBaseSpec.getTask());
        try {
            // Retrieve Executable
            String executableId = runSpecAccessor.getVersion();
            Executable executable = executableEntityServiceProvider
                .getEntityServiceByRuntime(runSpecAccessor.getRuntime())
                .get(executableId);

            // Retrieve state machine
            Fsm<State, RunEvent, Map<String, Serializable>> fsm = createFsm(run);

            fsm
                .getState(State.RUNNING)
                .getTransaction(RunEvent.STOP)
                .setInternalLogic((context, input, stateMachine) -> {
                    if (!Optional.ofNullable(runBaseSpec.getLocalExecution()).orElse(Boolean.FALSE)) {
                        // Retrieve Runtime and build run
                        Runtime<
                            ? extends ExecutableBaseSpec,
                            ? extends RunBaseSpec,
                            ? extends RunBaseStatus,
                            ? extends RunRunnable
                        > runtime = runtimeFactory.getRuntime(executable.getKind());
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

                return entityService.update(run.getId(), run);
            } catch (InvalidTransactionException e) {
                // log error
                log.debug("Invalid transaction from state {}  to state {}", e.getFromState(), e.getToState());
                throw e;
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    public Run delete(@NotNull Run run) throws NoSuchEntityException {
        // GET state machine, init state machine with status
        RunBaseSpec runBaseSpec = new RunBaseSpec();
        runBaseSpec.configure(run.getSpec());
        RunSpecAccessor runSpecAccessor = RunUtils.parseTask(runBaseSpec.getTask());
        try {
            // Retrieve Executable
            String executableId = runSpecAccessor.getVersion();
            Executable executable = executableEntityServiceProvider
                .getEntityServiceByRuntime(runSpecAccessor.getRuntime())
                .get(executableId);

            // Retrieve state machine
            Fsm<State, RunEvent, Map<String, Serializable>> fsm = createFsm(run);

            fsm
                .getState(State.RUNNING)
                .getTransaction(RunEvent.DELETING)
                .setInternalLogic((context, input, stateMachine) -> {
                    if (!Optional.ofNullable(runBaseSpec.getLocalExecution()).orElse(Boolean.FALSE)) {
                        // Retrieve Runtime and build run
                        Runtime<
                            ? extends ExecutableBaseSpec,
                            ? extends RunBaseSpec,
                            ? extends RunBaseStatus,
                            ? extends RunRunnable
                        > runtime = runtimeFactory.getRuntime(executable.getKind());
                        // Create Runnable
                        RunRunnable runnable = runtime.delete(run);

                        return Optional.ofNullable(runnable);
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
                            ? extends ExecutableBaseSpec,
                            ? extends RunBaseSpec,
                            ? extends RunBaseStatus,
                            ? extends RunRunnable
                        > runtime = runtimeFactory.getRuntime(executable.getKind());
                        // Create Runnable
                        RunRunnable runnable = runtime.delete(run);

                        return Optional.ofNullable(runnable);
                    } else {
                        return Optional.empty();
                    }
                });
            fsm
                .getState(State.ERROR)
                .getTransaction(RunEvent.DELETING)
                .setInternalLogic((context, input, stateMachine) -> {
                    if (!Optional.ofNullable(runBaseSpec.getLocalExecution()).orElse(Boolean.FALSE)) {
                        // Retrieve Runtime and build run
                        Runtime<
                            ? extends ExecutableBaseSpec,
                            ? extends RunBaseSpec,
                            ? extends RunBaseStatus,
                            ? extends RunRunnable
                        > runtime = runtimeFactory.getRuntime(executable.getKind());
                        // Create Runnable
                        RunRunnable runnable = runtime.delete(run);

                        return Optional.ofNullable(runnable);
                    } else {
                        return Optional.empty();
                    }
                });

            fsm
                .getState(State.COMPLETED)
                .getTransaction(RunEvent.DELETING)
                .setInternalLogic((context, input, stateMachine) -> {
                    if (!Optional.ofNullable(runBaseSpec.getLocalExecution()).orElse(Boolean.FALSE)) {
                        // Retrieve Runtime and build run
                        Runtime<
                            ? extends ExecutableBaseSpec,
                            ? extends RunBaseSpec,
                            ? extends RunBaseStatus,
                            ? extends RunRunnable
                        > runtime = runtimeFactory.getRuntime(executable.getKind());
                        // Create Runnable
                        RunRunnable runnable = runtime.delete(run);

                        return Optional.ofNullable(runnable);
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
                Run updated = entityService.update(run.getId(), run);

                //if runnable we are deleting in async, otherwise move to delete
                if (runnable.isEmpty()) {
                    //dispatch event
                    onDeleted(updated, null);
                }

                return updated;
            } catch (InvalidTransactionException e) {
                // log error
                log.debug("Invalid transaction from state {}  to state {}", e.getFromState(), e.getToState());
                throw e;
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Async
    @EventListener
    public void onChangedEvent(RunnableChangedEvent<RunRunnable> event) throws StoreException {
        if (event.getRunMonitorObject() == null) {
            return;
        }

        // Retrieve the RunMonitorObject from the event
        RunnableMonitorObject runnableMonitorObject = event.getRunMonitorObject();

        // Use service to retrieve the run and check if state is changed
        Optional
            .ofNullable(entityService.find(runnableMonitorObject.getRunId()))
            .ifPresentOrElse(
                run -> {
                    try {
                        if (
                            //either signal an update or track progress (running state)
                            !Objects.equals(
                                StatusFieldAccessor.with(run.getStatus()).getState(),
                                runnableMonitorObject.getStateId()
                            ) ||
                            State.RUNNING == State.valueOf(runnableMonitorObject.getStateId())
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
                                    log.debug(
                                        "State {} for run id {} not managed",
                                        runnableMonitorObject.getStateId(),
                                        runnableMonitorObject.getRunId()
                                    );
                                    break;
                            }
                        } else {
                            log.debug(
                                "State {} for run id {} not changed",
                                runnableMonitorObject.getStateId(),
                                runnableMonitorObject.getRunId()
                            );
                        }
                    } catch (StoreException e) {
                        log.error("store error for {}:{}", runnableMonitorObject.getRunId(), e.getMessage());
                    }
                },
                () -> {
                    log.error("Run with id {} not found", runnableMonitorObject.getRunId());
                }
            );
    }

    // Callback Methods
    private void onRunning(Run run, RunnableChangedEvent<RunRunnable> event)
        throws NoSuchEntityException, StoreException {
        // Try to move forward state machine based on current state
        Fsm<State, RunEvent, Map<String, Serializable>> fsm = createFsm(run);

        // Retrieve Runtime
        Executable executable = retrieveExecutable(run);

        // Retrieve Runtime
        Runtime<
            ? extends ExecutableBaseSpec,
            ? extends RunBaseSpec,
            ? extends RunBaseStatus,
            ? extends RunRunnable
        > runtime = runtimeFactory.getRuntime(executable.getKind());

        // Define logic for state READY
        fsm
            .getState(State.READY)
            .getTransaction(RunEvent.EXECUTE)
            .setInternalLogic((context, input, fsmInstance) -> {
                log.debug(
                    "Executing internal logic for state RUNNING, " + "event :{}, input: {}",
                    RunEvent.EXECUTE,
                    input
                );
                if (log.isTraceEnabled()) {
                    log.trace("Executing internal logic for state RUNNING, " + "context: {}", context);
                }

                RunRunnable runnable = event != null ? event.getRunnable() : null;
                RunBaseStatus runStatus = runtime.onRunning(run, runnable);
                return Optional.ofNullable(runStatus);
            });
        fsm
            .getState(State.RUNNING)
            .getTransaction(RunEvent.LOOP)
            .setInternalLogic((context, input, fsmInstance) -> {
                log.debug(
                    "Executing internal logic for state RUNNING, " + "event :{},  input: {}",
                    RunEvent.LOOP,
                    input
                );
                if (log.isTraceEnabled()) {
                    log.trace("Executing internal logic for state RUNNING, " + "context: {}", context);
                }
                RunRunnable runnable = event != null ? event.getRunnable() : null;
                RunBaseStatus runStatus = runtime.onRunning(run, runnable);
                return Optional.ofNullable(runStatus);
            });
        try {
            //TODO call registry processor to retrieve all processor for onRunning and call process()
            Optional<RunBaseStatus> runStatus = fsm.goToState(State.RUNNING, null);

            // Update run status
            runStatus.ifPresentOrElse(
                runBaseStatus -> {

                    RunRunnable runnable = event != null ? event.getRunnable() : null;

                    // Iterate over all processor and store all RunBaseStatus as optional
                    List<RunBaseStatus> processorsStatus = processorRegistry.getProcessors("onRunning").stream().map(
                            processor -> processor.process(run, runnable, runBaseStatus)
                    ).collect(Collectors.toList());


                    // Do merge all status

                    run.setStatus(
                        MapUtils.mergeMultipleMaps(
                                runBaseStatus.toMap(),
                                Map.of("state", State.RUNNING.toString()),
                                )
                    );
                },
                () -> {
                    // Update run state to RUNNING
                    run.getStatus().put("state", State.RUNNING.toString());
                }
            );
            entityService.update(run.getId(), run);
        } catch (InvalidTransactionException e) {
            log.debug("Invalid transaction from state {}  to state {}", e.getFromState(), e.getToState());
        }
    }

    /**
     * A callback method to handle complete, update run status and execute internal logic for the COMPLETE state.
     *
     * @param event the RunChangedEvent triggering the error
     * @throws StoreException
     * @throws NoSuchEntityException if the entity being accessed does not exist
     */
    private void onCompleted(Run run, RunnableChangedEvent<RunRunnable> event)
        throws NoSuchEntityException, StoreException {
        // Try to move forward state machine based on current state
        Fsm<State, RunEvent, Map<String, Serializable>> fsm = createFsm(run);

        // Retrieve Runtime
        Executable executable = retrieveExecutable(run);

        // Retrieve Runtime
        Runtime<
            ? extends ExecutableBaseSpec,
            ? extends RunBaseSpec,
            ? extends RunBaseStatus,
            ? extends RunRunnable
        > runtime = runtimeFactory.getRuntime(executable.getKind());

        // Define logic for state RUNNING
        fsm
            .getState(State.RUNNING)
            .getTransaction(RunEvent.COMPLETE)
            .setInternalLogic((context, input, fsmInstance) -> {
                log.debug(
                    "Executing internal logic for state RUNNING, " + "event :{}, input: {}",
                    RunEvent.COMPLETE,
                    input
                );
                if (log.isTraceEnabled()) {
                    log.trace("Executing internal logic for state RUNNING, " + "context: {}", context);
                }
                RunRunnable runnable = event != null ? event.getRunnable() : null;
                RunBaseStatus runStatus = runtime.onComplete(run, runnable);
                return Optional.ofNullable(runStatus);
            });

        fsm
            .getState(State.COMPLETED)
            .getTransaction(RunEvent.DELETING)
            .setInternalLogic((context, input, fsmInstance) -> {
                log.debug(
                    "Executing internal logic for state COMPLETED, " + "event :{}, input: {}",
                    RunEvent.DELETING,
                    input
                );
                if (log.isTraceEnabled()) {
                    log.trace("Executing internal logic for state COMPLETED, " + "context: {}", context);
                }
                RunRunnable runnable = runtime.delete(run);
                return Optional.ofNullable(runnable);
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
            Run updated = entityService.update(run.getId(), run);

            //dispatch delete, we are in a final state
            Optional<RunRunnable> runnable = fsm.goToState(State.DELETING, null);
            runnable.ifPresent(runEvent -> {
                //dispatch event
                eventPublisher.publishEvent(runEvent);
            });
            if (runnable.isEmpty()) {
                //directly dispatch callback event
                onDeleted(updated, null);
            }
        } catch (InvalidTransactionException e) {
            log.debug("Invalid transaction from state {}  to state {}", e.getFromState(), e.getToState());
        }
    }

    /**
     * A callback method to handle stop in the run, update run status and execute internal logic for the STOP state.
     *
     * @param event the RunChangedEvent triggering the stop
     * @throws StoreException
     * @throws NoSuchEntityException if the entity being accessed does not exist
     */
    private void onStopped(Run run, RunnableChangedEvent<RunRunnable> event)
        throws NoSuchEntityException, StoreException {
        // Try to move forward state machine based on current state
        Fsm<State, RunEvent, Map<String, Serializable>> fsm = createFsm(run);

        // Retrieve Runtime
        Executable executable = retrieveExecutable(run);

        // Retrieve Runtime
        Runtime<
            ? extends ExecutableBaseSpec,
            ? extends RunBaseSpec,
            ? extends RunBaseStatus,
            ? extends RunRunnable
        > runtime = runtimeFactory.getRuntime(executable.getKind());

        // Define logic for state STOP
        fsm
            .getState(State.STOP)
            .getTransaction(RunEvent.STOP)
            .setInternalLogic((context, input, fsmInstance) -> {
                log.debug("Executing internal logic for state STOP, " + "event :{}, input: {}", RunEvent.STOP, input);
                if (log.isTraceEnabled()) {
                    log.trace("Executing internal logic for state STOP, " + "context: {}", context);
                }
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
            log.debug("Invalid transaction from state {}  to state {}", e.getFromState(), e.getToState());
        }
    }

    /**
     * A callback method to handle errors in the run, update run status and execute internal logic for the ERROR state.
     *
     * @param event the RunChangedEvent triggering the error
     * @throws StoreException
     * @throws NoSuchEntityException if the entity being accessed does not exist
     */
    private void onError(Run run, RunnableChangedEvent<RunRunnable> event)
        throws NoSuchEntityException, StoreException {
        // Try to move forward state machine based on current state
        Fsm<State, RunEvent, Map<String, Serializable>> fsm = createFsm(run);
        try {
            // Retrieve Runtime
            Executable executable = retrieveExecutable(run);

            Runtime<
                ? extends ExecutableBaseSpec,
                ? extends RunBaseSpec,
                ? extends RunBaseStatus,
                ? extends RunRunnable
            > runtime = runtimeFactory.getRuntime(executable.getKind());

            fsm
                .getState(State.RUNNING)
                .getTransaction(RunEvent.ERROR)
                .setInternalLogic((context, input, fsmInstance) -> {
                    log.debug(
                        "Executing internal logic for state RUNNING, " + "event :{}, input: {}",
                        RunEvent.ERROR,
                        input
                    );
                    if (log.isTraceEnabled()) {
                        log.trace("Executing internal logic for state RUNNING, " + "context: {}", context);
                    }
                    RunRunnable runnable = event != null ? event.getRunnable() : null;
                    RunBaseStatus runStatus = runtime.onError(run, runnable);
                    return Optional.ofNullable(runStatus);
                });

            fsm
                .getState(State.STOP)
                .getTransaction(RunEvent.ERROR)
                .setInternalLogic((context, input, fsmInstance) -> {
                    log.debug(
                        "Executing internal logic for state STOP, " + "event :{}, input: {}",
                        RunEvent.ERROR,
                        input
                    );
                    if (log.isTraceEnabled()) {
                        log.trace("Executing internal logic for state STOP, " + "context: {}", context);
                    }
                    RunRunnable runnable = event != null ? event.getRunnable() : null;
                    RunBaseStatus runStatus = runtime.onError(run, runnable);
                    return Optional.ofNullable(runStatus);
                });

            fsm
                .getState(State.ERROR)
                .getTransaction(RunEvent.DELETING)
                .setInternalLogic((context, input, fsmInstance) -> {
                    log.debug(
                        "Executing internal logic for state ERROR, " + "event :{}, input: {}",
                        RunEvent.DELETING,
                        input
                    );
                    if (log.isTraceEnabled()) {
                        log.trace("Executing internal logic for state ERROR, " + "context: {}", context);
                    }
                    RunRunnable runnable = runtime.delete(run);
                    return Optional.ofNullable(runnable);
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

                //update
                Run updated = entityService.update(run.getId(), run);

                //dispatch delete, we are in a final state
                Optional<RunRunnable> runnable = fsm.goToState(State.DELETING, null);
                runnable.ifPresent(runEvent -> {
                    //dispatch event
                    eventPublisher.publishEvent(runEvent);
                });
                if (runnable.isEmpty()) {
                    //directly dispatch callback event
                    onDeleted(updated, null);
                }
            } catch (InvalidTransactionException e) {
                log.debug("Invalid transaction from state {} to state {}", e.getFromState(), e.getToState());
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    /**
     * A method to handle delete in the run, update run status and execute internal logic for the STOP state.
     *
     * @param event the RunChangedEvent triggering the delete
     * @throws StoreException
     * @throws NoSuchEntityException if the entity being accessed does not exist
     */
    private void onDeleted(Run run, RunnableChangedEvent<RunRunnable> event)
        throws NoSuchEntityException, StoreException {
        // Try to move forward state machine based on current state
        Fsm<State, RunEvent, Map<String, Serializable>> fsm = createFsm(run);

        //check if we are in a deleting or finalize flow
        String curState = StatusFieldAccessor.with(run.getStatus()).getState();
        boolean toDelete = State.DELETING.name().equals(curState);

        // Retrieve Runtime
        Executable executable = retrieveExecutable(run);
        Runtime<
            ? extends ExecutableBaseSpec,
            ? extends RunBaseSpec,
            ? extends RunBaseStatus,
            ? extends RunRunnable
        > runtime = runtimeFactory.getRuntime(executable.getKind());

        // Define logic for state DELETING
        fsm
            .getState(State.DELETING)
            .getTransaction(RunEvent.DELETING)
            .setInternalLogic((context, input, fsmInstance) -> {
                log.debug(
                    "Executing internal logic for state DELETING, " + "event :{}, input: {}",
                    RunEvent.DELETING,
                    input
                );
                if (log.isTraceEnabled()) {
                    log.trace("Executing internal logic for state DELETING, " + "context: {}", context);
                }
                RunRunnable runnable = event != null ? event.getRunnable() : null;
                RunBaseStatus runStatus = runtime.onDeleted(run, runnable);
                return Optional.ofNullable(runStatus);
            });

        // Define logic for state ERROR
        fsm
            .getState(State.ERROR)
            .getTransaction(RunEvent.DELETING)
            .setInternalLogic((context, input, fsmInstance) -> {
                log.debug(
                    "Executing internal logic for state ERROR, " + "event :{}, input: {}",
                    RunEvent.DELETING,
                    input
                );
                if (log.isTraceEnabled()) {
                    log.trace("Executing internal logic for state ERROR, " + "context: {}", context);
                }
                RunRunnable runnable = event != null ? event.getRunnable() : null;
                RunBaseStatus runStatus = runtime.onDeleted(run, runnable);
                return Optional.ofNullable(runStatus);
            });

        // Define logic for state COMPLETED
        fsm
            .getState(State.COMPLETED)
            .getTransaction(RunEvent.DELETING)
            .setInternalLogic((context, input, fsmInstance) -> {
                log.debug(
                    "Executing internal logic for state COMPLETED, " + "event :{}, input: {}",
                    RunEvent.DELETING,
                    input
                );
                if (log.isTraceEnabled()) {
                    log.trace("Executing internal logic for state COMPLETED, " + "context: {}", context);
                }
                RunRunnable runnable = event != null ? event.getRunnable() : null;
                RunBaseStatus runStatus = runtime.onDeleted(run, runnable);
                return Optional.ofNullable(runStatus);
            });

        try {
            Optional<RunBaseStatus> runStatus = fsm.goToState(State.DELETED, null);
            runStatus.ifPresentOrElse(
                runBaseStatus -> {
                    if (toDelete) {
                        run.setStatus(
                            MapUtils.mergeMultipleMaps(runBaseStatus.toMap(), Map.of("state", State.DELETED.toString()))
                        );
                    } else {
                        run.setStatus(runBaseStatus.toMap());
                    }
                },
                () -> {
                    if (toDelete) {
                        // Update run state to DELETED
                        run.getStatus().put("state", State.DELETED.toString());
                    }
                }
            );

            //update run
            //explicit before delete to let external event handlers receive the msg
            entityService.update(run.getId(), run);

            if (toDelete) {
                //delete run via service to handle cascade
                runService.deleteRun(run.getId(), Boolean.TRUE);
            }
        } catch (InvalidTransactionException e) {
            log.debug("Invalid transaction from state {}  to state {}", e.getFromState(), e.getToState());
        }
    }

    /**
     * Creates and returns a finite state machine (FSM) with the specified initial state and context and run.
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
            if (log.isTraceEnabled()) {
                log.trace("State Change Listener: {}, context: {}", state, context);
            }
        });
        return fsm;
    }

    /**
     * Retrieve an executable based on the given run.
     *
     * @param run the run to retrieve the executable for
     * @return the retrieved executable
     * @throws StoreException
     */
    private Executable retrieveExecutable(Run run) throws NoSuchEntityException, StoreException {
        // GET state machine, init state machine with status
        RunBaseSpec runBaseSpec = new RunBaseSpec();
        runBaseSpec.configure(run.getSpec());
        RunSpecAccessor runSpecAccessor = RunUtils.parseTask(runBaseSpec.getTask());

        // Retrieve Executable
        String executableId = runSpecAccessor.getVersion();
        return executableEntityServiceProvider
            .getEntityServiceByRuntime(runSpecAccessor.getRuntime())
            .get(executableId);
    }

    private Specification<TaskEntity> createExecutableSpecification(String executable) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("function"), executable);
    }

    private Specification<TaskEntity> createTaskKindSpecification(String kind) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("kind"), kind);
    }
}
