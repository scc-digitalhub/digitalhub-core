package it.smartcommunitylabdhub.core.components.run;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.infrastructure.Runtime;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import it.smartcommunitylabdhub.commons.services.RunService;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.core.components.infrastructure.processors.ProcessorRegistry;
import it.smartcommunitylabdhub.core.components.infrastructure.runtimes.RuntimeFactory;
import it.smartcommunitylabdhub.core.fsm.RunContext;
import it.smartcommunitylabdhub.core.fsm.RunEvent;
import it.smartcommunitylabdhub.core.fsm.RunStateMachineFactory;
import it.smartcommunitylabdhub.core.models.entities.RunEntity;
import it.smartcommunitylabdhub.core.models.events.EntityAction;
import it.smartcommunitylabdhub.core.models.events.EntityOperation;
import it.smartcommunitylabdhub.fsm.Fsm;
import it.smartcommunitylabdhub.fsm.exceptions.InvalidTransitionException;
import it.smartcommunitylabdhub.runtimes.events.RunnableChangedEvent;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class RunLifecycleManager extends LifecycleManager<Run, RunEntity> {

    @Autowired
    private RunStateMachineFactory fsmFactory;

    @Autowired
    private RuntimeFactory runtimeFactory;

    @Autowired
    ProcessorRegistry processorRegistry;

    @Autowired
    private RunService runService;

    /*
     * Actions: ask to perform a change
     */
    public Run build(@NotNull Run run) {
        log.debug("build run with id {}", run.getId());
        if (log.isTraceEnabled()) {
            log.trace("run: {}", run);
        }

        //execute an update op for the event
        //NOTE: custom execution because action does *not* return a runnable
        //TODO cleanup

        // read
        RunSpecAccessor runSpecAccessor = RunSpecAccessor.with(run.getSpec());

        //resolve runtime either from runSpec or from taskSpec
        Runtime<?, ?, ?, ?> runtime = runSpecAccessor.getRuntime() != null
            ? runtimeFactory.getRuntime(runSpecAccessor.getRuntime())
            : null;

        // build state machine on current context
        Fsm<State, RunEvent, RunContext, RunRunnable> fsm = fsm(run, runtime);

        //execute update op with locking
        return exec(
            new EntityOperation<>(run, EntityAction.UPDATE),
            r -> {
                try {
                    //perform via FSM
                    Optional<RunBaseSpec> spec = fsm.perform(RunEvent.BUILD, null);
                    State state = fsm.getCurrentState();

                    // Update run spec
                    spec.ifPresent(s -> r.setSpec(s.toMap()));

                    //update status
                    RunBaseStatus runBaseStatus = RunBaseStatus.with(r.getStatus());
                    runBaseStatus.setState(state.name());

                    //post process stage
                    String stage = "on" + StringUtils.capitalize(state.name().toLowerCase());
                    Map<String, Serializable> runStatusMap = postProcess(r, null, runBaseStatus, stage);

                    // update status
                    r.setStatus(MapUtils.mergeMultipleMaps(r.getStatus(), runStatusMap, runBaseStatus.toMap()));
                    return r;
                } catch (InvalidTransitionException e) {
                    log.debug("Invalid transition {} -> {}", e.getFromState(), e.getToState());
                    return r;
                }
            }
        );
    }

    public Run run(@NotNull Run run) {
        log.debug("run run with id {}", run.getId());
        if (log.isTraceEnabled()) {
            log.trace("run: {}", run);
        }

        //execute an update op for the event
        return perform(run, RunEvent.RUN);
    }

    public Run stop(@NotNull Run run) {
        log.debug("stop run with id {}", run.getId());
        if (log.isTraceEnabled()) {
            log.trace("run: {}", run);
        }

        //execute an update op for the event
        return perform(run, RunEvent.STOP);
    }

    public Run resume(@NotNull Run run) {
        log.debug("resume run with id {}", run.getId());
        if (log.isTraceEnabled()) {
            log.trace("run: {}", run);
        }

        //execute an update op for the event
        return perform(run, RunEvent.RESUME);
    }

    public Run delete(@NotNull Run run) {
        log.debug("delete run with id {}", run.getId());
        if (log.isTraceEnabled()) {
            log.trace("run: {}", run);
        }

        //execute an update op for the event
        return perform(
            run,
            RunEvent.DELETING,
            runnable -> {
                if (runnable != null) {
                    eventPublisher.publishEvent(runnable);
                } else {
                    //publish an op for direct action
                    //TODO refactor with a proper syntetic event
                    RunnableChangedEvent<RunRunnable> event = new RunnableChangedEvent<>();
                    event.setState(State.DELETED.name());
                    eventPublisher.publishEvent(event);
                }
            }
        );
    }

    @Async
    @EventListener
    public void receiveAction(LifecycleOperation<Run, RunEvent> event) {
        log.debug("receive operation for run {}: {}", event.getId(), event.getAction());

        Run run = event.getDto();
        if (log.isTraceEnabled()) {
            log.trace("run: {}", String.valueOf(run));
        }

        RunEvent action = event.getAction();

        switch (action) {
            case BUILD:
                build(run);
                break;
            case RUN:
                run(run);
                break;
            case STOP:
                stop(run);
                break;
            case RESUME:
                resume(run);
                break;
            case DELETING:
                delete(run);
                break;
            default:
                log.debug("Action {} not managed", action);
                break;
        }
    }

    /*
     * Events: react to a change
     */
    @Async
    @EventListener
    public void receiveEvent(RunnableChangedEvent<RunRunnable> event) {
        if (event.getState() == null) {
            return;
        }

        log.debug("receive event for run {}: {}", event.getId(), event.getState());
        if (log.isTraceEnabled()) {
            log.trace("event: {}", event);
        }

        String id = event.getId();
        State state = State.valueOf(event.getState());

        // retrieve the run
        Run run = runService.findRun(id);
        if (run == null) {
            log.error("Run with id {} not found", id);
            return;
        }

        switch (state) {
            case COMPLETED:
                onCompleted(run, event.getRunnable());
                break;
            case ERROR:
                onError(run, event.getRunnable());
                break;
            case RUNNING:
                onRunning(run, event.getRunnable());
                break;
            case STOPPED:
                onStopped(run, event.getRunnable());
                break;
            case DELETED:
                onDeleted(run, event.getRunnable());
                break;
            default:
                log.debug("State {} not managed", state);
                break;
        }
    }

    public Run onRunning(Run run, RunRunnable runnable) {
        log.debug("onRunning run with id {}", run.getId());
        if (log.isTraceEnabled()) {
            log.trace("run: {}", run);
        }

        //execute an update op for the event
        return handle(run, State.RUNNING, runnable);
    }

    public Run onStopped(Run run, RunRunnable runnable) {
        log.debug("onStopped run with id {}", run.getId());
        if (log.isTraceEnabled()) {
            log.trace("run: {}", run);
        }

        //execute an update op for the event
        return handle(run, State.STOPPED, runnable);
    }

    public Run onCompleted(Run run, RunRunnable runnable) {
        log.debug("onCompleted run with id {}", run.getId());
        if (log.isTraceEnabled()) {
            log.trace("run: {}", run);
        }

        //execute an update op for the event
        Run r = handle(run, State.COMPLETED, runnable);

        //cleanup
        if (runnable != null) {
            // read
            RunSpecAccessor runSpecAccessor = RunSpecAccessor.with(r.getSpec());

            //resolve runtime either from runSpec or from taskSpec
            Runtime<?, ?, ?, ?> runtime = runSpecAccessor.getRuntime() != null
                ? runtimeFactory.getRuntime(runSpecAccessor.getRuntime())
                : null;

            if (runtime == null) {
                throw new IllegalArgumentException("invalid or unsupported runtime");
            }

            //publish event for framework if required
            Optional.ofNullable(runtime.delete(r)).ifPresent(rb -> eventPublisher.publishEvent(rb));
        }

        return r;
    }

    public Run onError(Run run, RunRunnable runnable) {
        log.debug("onError run with id {}", run.getId());
        if (log.isTraceEnabled()) {
            log.trace("run: {}", run);
        }

        //execute an update op for the event
        Run r = handle(run, State.ERROR, runnable);

        //cleanup
        if (runnable != null) {
            // read
            RunSpecAccessor runSpecAccessor = RunSpecAccessor.with(r.getSpec());

            //resolve runtime either from runSpec or from taskSpec
            Runtime<?, ?, ?, ?> runtime = runSpecAccessor.getRuntime() != null
                ? runtimeFactory.getRuntime(runSpecAccessor.getRuntime())
                : null;

            if (runtime == null) {
                throw new IllegalArgumentException("invalid or unsupported runtime");
            }

            //publish event for framework if required
            Optional.ofNullable(runtime.delete(r)).ifPresent(rb -> eventPublisher.publishEvent(rb));
        }

        return r;
    }

    public void onDeleted(Run run, RunRunnable runnable) {
        log.debug("onDeleted run with id {}", run.getId());
        if (log.isTraceEnabled()) {
            log.trace("run: {}", run);
        }

        //check if we are in a deleting or finalize flow
        String curState = StatusFieldAccessor.with(run.getStatus()).getState();
        boolean toDelete = State.DELETING.name().equals(curState);

        if (!toDelete) {
            //callback after a cleanup, no-op
            return;
        }

        log.debug("execute DELETED for run with id {}", run.getId());

        //execute an update op for the event
        handle(run, State.DELETED, runnable);

        //delete run via service to handle cascade
        runService.deleteRun(run.getId(), Boolean.TRUE);
    }

    /*
     * Async: receive action event
     */

    /*
     * Internals
     */
    public Run perform(Run run, RunEvent event) {
        //by default publish as event
        return perform(
            run,
            event,
            runnable -> {
                if (runnable != null) {
                    eventPublisher.publishEvent(runnable);
                }
            }
        );
    }

    public Run perform(Run run, RunEvent event, @Nullable Consumer<RunRunnable> effect) {
        // read
        RunSpecAccessor runSpecAccessor = RunSpecAccessor.with(run.getSpec());

        //resolve runtime either from runSpec or from taskSpec
        Runtime<?, ?, ?, ?> runtime = runSpecAccessor.getRuntime() != null
            ? runtimeFactory.getRuntime(runSpecAccessor.getRuntime())
            : null;

        if (runtime == null) {
            throw new IllegalArgumentException("invalid or unsupported runtime");
        }

        // build state machine on current context
        Fsm<State, RunEvent, RunContext, RunRunnable> fsm = fsm(run, runtime);

        //execute update op with locking
        return exec(
            new EntityOperation<>(run, EntityAction.UPDATE),
            r -> {
                try {
                    //perform via FSM
                    Optional<RunRunnable> runnable = fsm.perform(event, null);
                    State state = fsm.getCurrentState();

                    //update status
                    RunBaseStatus runBaseStatus = RunBaseStatus.with(r.getStatus());
                    runBaseStatus.setState(state.name());
                    runnable.ifPresent(rb -> runBaseStatus.setMessage(rb.getMessage()));

                    //post process stage
                    String stage = "on" + StringUtils.capitalize(state.name().toLowerCase());
                    Map<String, Serializable> runStatusMap = postProcess(
                        r,
                        runnable.orElse(null),
                        runBaseStatus,
                        stage
                    );

                    // update status
                    r.setStatus(MapUtils.mergeMultipleMaps(r.getStatus(), runStatusMap, runBaseStatus.toMap()));

                    //side effect
                    if (effect != null) {
                        effect.accept(runnable.orElse(null));
                    }

                    return r;
                } catch (InvalidTransitionException e) {
                    log.debug("Invalid transition {} -> {} for event {}", e.getFromState(), e.getToState(), event);
                    return r;
                }
            }
        );
    }

    public Run handle(Run run, State nexState, @Nullable RunRunnable runRunnable) {
        return handle(run, nexState, runRunnable, null);
    }

    public Run handle(
        Run run,
        State nexState,
        @Nullable RunRunnable runRunnable,
        @Nullable Consumer<RunBaseStatus> effect
    ) {
        // read
        RunSpecAccessor runSpecAccessor = RunSpecAccessor.with(run.getSpec());

        //resolve runtime either from runSpec or from taskSpec
        Runtime<?, ?, ?, ?> runtime = runSpecAccessor.getRuntime() != null
            ? runtimeFactory.getRuntime(runSpecAccessor.getRuntime())
            : null;

        if (runtime == null) {
            throw new IllegalArgumentException("invalid or unsupported runtime");
        }

        // build state machine on current context
        Fsm<State, RunEvent, RunContext, RunRunnable> fsm = fsm(run, runtime);

        //execute update op with locking
        return exec(
            new EntityOperation<>(run, EntityAction.UPDATE),
            r -> {
                try {
                    //perform via FSM
                    Optional<RunBaseStatus> status = fsm.goToState(nexState, runRunnable);
                    State state = fsm.getCurrentState();

                    //update status
                    RunBaseStatus runBaseStatus = status.orElse(RunBaseStatus.with(r.getStatus()));
                    runBaseStatus.setState(state.name());

                    //effect
                    if (effect != null) {
                        effect.accept(runBaseStatus);
                    }

                    //post process stage
                    String stage = "on" + StringUtils.capitalize(state.name().toLowerCase());
                    Map<String, Serializable> runStatusMap = postProcess(r, runRunnable, runBaseStatus, stage);

                    // update status
                    r.setStatus(MapUtils.mergeMultipleMaps(r.getStatus(), runStatusMap, runBaseStatus.toMap()));
                    return r;
                } catch (InvalidTransitionException e) {
                    log.debug("Invalid transition {} -> {}", e.getFromState(), e.getToState());
                    return r;
                }
            }
        );
    }

    /*
     * Processors
     * TODO cleanup and refactor
     */

    private Map<String, Serializable> postProcess(
        Run run,
        RunRunnable runnable,
        RunBaseStatus runBaseStatus,
        String stage
    ) {
        // Iterate over all processor and store all RunBaseStatus as optional
        List<RunBaseStatus> processorsStatus = processorRegistry
            .getProcessors(stage)
            .stream()
            .map(processor -> processor.process(run, runnable, runBaseStatus))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        return processorsStatus.stream().map(RunBaseStatus::toMap).reduce(new HashMap<>(), MapUtils::mergeMultipleMaps);
    }

    private Fsm<State, RunEvent, RunContext, RunRunnable> fsm(Run run, Runtime<?, ?, ?, ?> runtime) {
        //initial state is run current state
        State initialState = State.valueOf(StatusFieldAccessor.with(run.getStatus()).getState());
        RunContext context = RunContext.builder().run(run).runtime(runtime).build();

        // create state machine via factory
        return fsmFactory.create(initialState, context);
    }
}
