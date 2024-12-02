package it.smartcommunitylabdhub.core.components.run;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.infrastructure.Actuator;
import it.smartcommunitylabdhub.commons.infrastructure.TriggerRun;
import it.smartcommunitylabdhub.commons.models.enums.RelationshipName;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.metadata.RelationshipsMetadata;
import it.smartcommunitylabdhub.commons.models.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.models.trigger.Trigger;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerBaseSpec;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerBaseStatus;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerRunBaseStatus;
import it.smartcommunitylabdhub.commons.services.RunService;
import it.smartcommunitylabdhub.commons.services.TaskService;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.core.components.infrastructure.runtimes.ActuatorFactory;
import it.smartcommunitylabdhub.core.fsm.TriggerContext;
import it.smartcommunitylabdhub.core.fsm.TriggerEvent;
import it.smartcommunitylabdhub.core.fsm.TriggerFsmFactory;
import it.smartcommunitylabdhub.core.models.entities.TriggerEntity;
import it.smartcommunitylabdhub.core.models.events.EntityAction;
import it.smartcommunitylabdhub.core.models.events.EntityOperation;
import it.smartcommunitylabdhub.fsm.Fsm;
import it.smartcommunitylabdhub.fsm.exceptions.InvalidTransitionException;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;

@Slf4j
@Component
public class TriggerLifecycleManager extends LifecycleManager<Trigger, TriggerEntity> {

    @Autowired
    private TriggerFsmFactory fsmFactory;

    @Autowired
    private ActuatorFactory actuatorFactory;

    @Autowired
    private TaskService taskService;

    @Autowired
    private RunService runService;

    @Autowired
    private RunLifecycleManager runManager;

    /*
     * Actions: ask to perform
     */

    public Trigger run(@NotNull Trigger trigger) {
        log.debug("run trigger with id {}", trigger.getId());
        if (log.isTraceEnabled()) {
            log.trace("trigger: {}", trigger);
        }

        //resolve actuator
        Actuator<? extends TriggerBaseSpec, ? extends TriggerBaseStatus, ? extends TriggerRunBaseStatus> actuator =
            actuatorFactory.getActuator(trigger.getKind());

        // build state machine on current context
        Fsm<State, TriggerEvent, TriggerContext, TriggerRun> fsm = fsm(trigger, actuator);

        //execute update op with locking
        return exec(
            new EntityOperation<>(trigger, EntityAction.UPDATE),
            tr -> {
                try {
                    //perform via FSM
                    Optional<TriggerBaseStatus> status = fsm.perform(TriggerEvent.RUN, null);
                    State state = fsm.getCurrentState();

                    Map<String, Serializable> actuatorStatus = status.isPresent() ? status.get().toMap() : null;

                    //update status
                    TriggerBaseStatus runBaseStatus = TriggerBaseStatus.with(tr.getStatus());
                    runBaseStatus.setState(state.name());

                    tr.setStatus(MapUtils.mergeMultipleMaps(tr.getStatus(), actuatorStatus, runBaseStatus.toMap()));
                    return tr;
                } catch (InvalidTransitionException e) {
                    log.debug("Invalid transition {} -> {}", e.getFromState(), e.getToState());
                    return tr;
                }
            }
        );
    }

    public Trigger stop(@NotNull Trigger trigger) {
        log.debug("stop trigger with id {}", trigger.getId());
        if (log.isTraceEnabled()) {
            log.trace("trigger: {}", trigger);
        }

        //resolve actuator
        Actuator<? extends TriggerBaseSpec, ? extends TriggerBaseStatus, ? extends TriggerRunBaseStatus> actuator =
            actuatorFactory.getActuator(trigger.getKind());

        // build state machine on current context
        Fsm<State, TriggerEvent, TriggerContext, TriggerRun> fsm = fsm(trigger, actuator);

        //execute update op with locking
        return exec(
            new EntityOperation<>(trigger, EntityAction.UPDATE),
            tr -> {
                try {
                    //perform via FSM
                    Optional<TriggerBaseStatus> status = fsm.perform(TriggerEvent.STOP, null);
                    State state = fsm.getCurrentState();

                    Map<String, Serializable> actuatorStatus = status.isPresent() ? status.get().toMap() : null;

                    //update status
                    TriggerBaseStatus runBaseStatus = TriggerBaseStatus.with(tr.getStatus());
                    runBaseStatus.setState(state.name());

                    tr.setStatus(MapUtils.mergeMultipleMaps(tr.getStatus(), actuatorStatus, runBaseStatus.toMap()));
                    return tr;
                } catch (InvalidTransitionException e) {
                    log.debug("Invalid transition {} -> {}", e.getFromState(), e.getToState());
                    return tr;
                }
            }
        );
    }

    public Trigger fire(@NotNull Trigger trigger) {
        log.debug("fire trigger with id {}", trigger.getId());
        if (log.isTraceEnabled()) {
            log.trace("trigger: {}", trigger);
        }

        //resolve actuator
        Actuator<? extends TriggerBaseSpec, ? extends TriggerBaseStatus, ? extends TriggerRunBaseStatus> actuator =
            actuatorFactory.getActuator(trigger.getKind());

        // build state machine on current context
        Fsm<State, TriggerEvent, TriggerContext, TriggerRun> fsm = fsm(trigger, actuator);

        TriggerRun instance = TriggerRun.builder().triggerId(trigger.getId()).timestamp(Instant.now()).build();

        //execute read op with locking
        return exec(
            new EntityOperation<>(trigger, EntityAction.READ),
            tr -> {
                try {
                    //perform via FSM
                    Optional<TriggerRunBaseStatus> status = fsm.perform(TriggerEvent.FIRE, instance);
                    TriggerRunBaseStatus trRunStatus = TriggerRunBaseStatus.builder().trigger(instance).build();
                    Map<String, Serializable> actuatorRunStatus = status.isPresent() ? status.get().toMap() : null;
                    Map<String, Serializable> triggerRunStatus = MapUtils.mergeMultipleMaps(
                        actuatorRunStatus,
                        trRunStatus.toMap()
                    );
                    State state = fsm.getCurrentState();

                    log.debug("build run from template for trigger {}", tr.getId());

                    //access task details from ref, same as run
                    RunSpecAccessor specAccessor = RunSpecAccessor.with(tr.getSpec());
                    if (!StringUtils.hasText(specAccessor.getTaskId())) {
                        throw new IllegalArgumentException("spec: invalid task");
                    }

                    //fetch for build
                    TriggerBaseSpec baseSpec = TriggerBaseSpec.from(tr.getSpec());
                    Task task = taskService.getTask(specAccessor.getTaskId());

                    //build meta
                    RelationshipsMetadata relMetadata = RelationshipsMetadata
                        .builder()
                        .relationships(List.of(new RelationshipDetail(RelationshipName.PRODUCEDBY, null, tr.getKey())))
                        .build();

                    //status
                    RunBaseStatus baseStatus = RunBaseStatus.baseBuilder().state(State.CREATED.name()).build();
                    Map<String, Serializable> runStatus = MapUtils.mergeMultipleMaps(
                        triggerRunStatus,
                        baseStatus.toMap()
                    );

                    //build run from trigger template
                    Run run = Run
                        .builder()
                        .kind(baseSpec.getRun())
                        .project(task.getProject())
                        .user(trigger.getUser())
                        .spec(baseSpec.getTemplate())
                        .metadata(relMetadata.toMap())
                        .status(runStatus)
                        .build();

                    if (log.isTraceEnabled()) {
                        log.trace("built run: {}", run);
                    }

                    //create run via service as CREATED
                    run = runService.createRun(run);

                    //build now
                    run = runManager.build(run);

                    //dispatch for run
                    //TODO evaluate detaching via async
                    run = runManager.run(run);

                    //update trigger status
                    //TODO evaluate storing trigger event
                    TriggerBaseStatus trStatus = TriggerBaseStatus.with(tr.getStatus());
                    trStatus.setState(state.name());

                    tr.setStatus(trStatus.toMap());
                    return tr;
                } catch (InvalidTransitionException e) {
                    log.debug("Invalid transition {} -> {}", e.getFromState(), e.getToState());
                    return tr;
                } catch (NoSuchEntityException | SystemException | DuplicatedEntityException | BindException e) {
                    log.error("Error creating run: {}", e.getMessage());
                    return tr;
                }
            }
        );
    }

    private Fsm<State, TriggerEvent, TriggerContext, TriggerRun> fsm(Trigger trigger, Actuator<?, ?, ?> actuator) {
        //initial state is current state
        State initialState = State.valueOf(StatusFieldAccessor.with(trigger.getStatus()).getState());
        TriggerContext context = TriggerContext.builder().trigger(trigger).actuator(actuator).build();

        // create state machine via factory
        return fsmFactory.create(initialState, context);
    }
}
