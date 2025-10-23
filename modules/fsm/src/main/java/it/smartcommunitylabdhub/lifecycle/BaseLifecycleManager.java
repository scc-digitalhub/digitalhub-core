/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * Copyright 2025 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.lifecycle;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.ProcessorRegistry;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.lifecycle.LifecycleEvent;
import it.smartcommunitylabdhub.commons.lifecycle.LifecycleEvents;
import it.smartcommunitylabdhub.commons.lifecycle.LifecycleState;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.metadata.Metadata;
import it.smartcommunitylabdhub.commons.models.metadata.MetadataDTO;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.status.Status;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.events.EntityAction;
import it.smartcommunitylabdhub.events.EntityOperation;
import it.smartcommunitylabdhub.fsm.Fsm;
import it.smartcommunitylabdhub.fsm.exceptions.InvalidTransitionException;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

@Slf4j
public abstract class BaseLifecycleManager<
    D extends BaseDTO & SpecDTO & StatusDTO & MetadataDTO,
    S extends Enum<S> & LifecycleState<D>,
    E extends Enum<E> & LifecycleEvents<D>
>
    extends AbstractLifecycleManager<D>
    implements LifecycleManager<D> {

    protected final Class<D> typeClass;
    protected final Class<S> stateClass;
    protected final Class<E> eventsClass;

    protected ProcessorRegistry<D, Metadata> metadataProcessorRegistry;
    protected ProcessorRegistry<D, Spec> specProcessorRegistry;
    protected ProcessorRegistry<D, Status> statusProcessorRegistry;

    protected Fsm.Factory<S, E, D> fsmFactory;

    @SuppressWarnings("unchecked")
    public BaseLifecycleManager() {
        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.typeClass = (Class<D>) t;
        Type ts = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[1];
        this.stateClass = (Class<S>) ts;
        Type te = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[2];
        this.eventsClass = (Class<E>) te;
    }

    protected BaseLifecycleManager(Class<D> typeClass, Class<S> stateClass, Class<E> eventsClass) {
        this.typeClass = typeClass;
        this.stateClass = stateClass;
        this.eventsClass = eventsClass;
    }

    @Autowired(required = false)
    public void setFsmFactory(Fsm.Factory<S, E, D> fsmFactory) {
        this.fsmFactory = fsmFactory;
    }

    @Autowired(required = false)
    public void setMetadataProcessorRegistry(ProcessorRegistry<D, Metadata> metadataProcessorRegistry) {
        this.metadataProcessorRegistry = metadataProcessorRegistry;
    }

    @Autowired(required = false)
    public void setSpecProcessorRegistry(ProcessorRegistry<D, Spec> specProcessorRegistry) {
        this.specProcessorRegistry = specProcessorRegistry;
    }

    @Autowired(required = false)
    public void setStatusProcessorRegistry(ProcessorRegistry<D, Status> statusProcessorRegistry) {
        this.statusProcessorRegistry = statusProcessorRegistry;
    }

    protected Fsm<S, E, D> fsm(D dto) {
        if (fsmFactory == null) {
            throw new IllegalStateException("FSM factory not set: provide or override");
        }

        //initial state is current state
        String state = StatusFieldAccessor.with(dto.getStatus()).getState();
        if (state == null) {
            throw new IllegalStateException("State not set");
        }

        //get enum via enum..
        S initialState = Enum.valueOf(stateClass, state);

        // create state machine via factory
        // context is the dto itself
        return fsmFactory.create(initialState, dto);
    }

    /*
     * Listen for event callbacks
     * DISABLED: we are producers of events not consumers!
     */
    // @Async
    // @EventListener
    // public void receive(LifecycleEvent<D> event) {
    //     if (event.getEvent() == null) {
    //         return;
    //     }
    //     try {
    //         log.debug("receive event {} for {}", event.getEvent(), event.getId());
    //         if (log.isTraceEnabled()) {
    //             log.trace("event: {}", event);
    //         }

    //         String id = event.getId();

    //         //load trigger from db
    //         D dto = entityService.find(event.getId());
    //         if (dto == null) {
    //             log.error("Entity with id {} not found", id);
    //             return;
    //         }

    //         //perform lifecycle operation as callback to event
    //         perform(dto, event.getEvent(), event);
    //     } catch (StoreException e) {
    //         log.error("Error with store", e.getMessage());
    //     }
    // }

    /*
     * Perform lifecycle operation from events
     */
    public D perform(@NotNull D dto, @NotNull String event) {
        return perform(dto, event, null, null);
    }

    public <I> D perform(@NotNull D dto, @NotNull String event, @Nullable I input) {
        return perform(dto, event, input, null);
    }

    public <I, R> D perform(
        @NotNull D dto,
        @NotNull String event,
        @Nullable I input,
        @Nullable BiConsumer<D, R> effect
    ) {
        log.debug("perform {} for {} with id {}", event, dto.getClass().getSimpleName().toLowerCase(), dto.getId());
        if (log.isTraceEnabled()) {
            log.trace("dto: {}", dto);
        }

        //handle event via FSM
        E lifecycleEvent = Enum.valueOf(eventsClass, event);
        D res = dto;
        try {
            res = transition(dto, fsm -> fsm.perform(lifecycleEvent, input), input, effect);
        } catch (CoreRuntimeException e) {
            //on ex try transitioning to ERROR
            log.debug("entity {} transition to {} generated an exception, move to ERROR", dto.getId());
            //merge message
            dto.setStatus(MapUtils.mergeMultipleMaps(dto.getStatus(), Map.of("message", e.getMessage())));
            //transition
            res = transition(dto, fsm -> fsm.goToState(Enum.valueOf(stateClass, "ERROR"), input), input, effect);
        }

        StatusFieldAccessor status = StatusFieldAccessor.with(res.getStatus());
        if ("DELETED".equals(status.getState())) {
            //custom handle DELETED: we have already performed cleanup either via logic or side effects
            //we can now remove the entity from the repository via op
            log.debug("entity {} is in DELETED state, removing from repository", dto.getId());
            res = exec(new EntityOperation<>(dto, EntityAction.DELETE), d1 -> d1);
        }

        //publish new event
        LifecycleEvent<D> e = LifecycleEvent
            .<D>builder()
            .id(res.getId())
            .kind(res.getKind())
            .user(res.getUser())
            .project(res.getProject())
            .event(lifecycleEvent.name())
            .state(status.getState())
            //append object to event
            .dto(res)
            .build();

        log.debug("publish event {} for {}", event, res.getId());
        if (log.isTraceEnabled()) {
            log.trace("event: {}", e);
        }
        this.eventPublisher.publishEvent(e);

        return res;
    }

    /*
     * Handle lifecycle events from state changes
     */
    public D handle(@NotNull D dto, String nexState) {
        return handle(dto, nexState, null, null);
    }

    public <I> D handle(@NotNull D dto, String nextState, @Nullable I input) {
        return handle(dto, nextState, input, null);
    }

    public <I, R> D handle(
        @NotNull D dto,
        String nextStateValue,
        @Nullable I input,
        @Nullable BiConsumer<D, R> effect
    ) {
        log.debug(
            "handle {} for {} with id {}",
            nextStateValue,
            dto.getClass().getSimpleName().toLowerCase(),
            dto.getId()
        );
        if (log.isTraceEnabled()) {
            log.trace("dto: {}", dto);
        }

        //transition to next state via FSM
        S nextState = Enum.valueOf(stateClass, nextStateValue);
        D res = dto;
        try {
            res = transition(dto, fsm -> fsm.goToState(nextState, input), input, effect);
        } catch (CoreRuntimeException e) {
            //on ex try transitioning to ERROR
            log.debug("entity {} transition to {} generated an exception, move to ERROR", dto.getId());
            //merge message
            dto.setStatus(MapUtils.mergeMultipleMaps(dto.getStatus(), Map.of("message", e.getMessage())));
            //transition
            res = transition(dto, fsm -> fsm.goToState(Enum.valueOf(stateClass, "ERROR"), input), input, effect);
        }
        StatusFieldAccessor status = StatusFieldAccessor.with(res.getStatus());
        if ("DELETED".equals(status.getState())) {
            //custom handle DELETED: we have already performed cleanup either via logic or side effects
            //we can now remove the entity from the repository via op
            log.debug("entity {} is in DELETED state, removing from repository", dto.getId());
            res = exec(new EntityOperation<>(dto, EntityAction.DELETE), d1 -> d1);
        }

        //publish new event
        LifecycleEvent<D> e = LifecycleEvent
            .<D>builder()
            .id(res.getId())
            .kind(res.getKind())
            .user(res.getUser())
            .project(res.getProject())
            .state(status.getState())
            //append object to event
            .dto(res)
            .build();

        log.debug("publish event on state {} for {}", nextState, res.getId());
        if (log.isTraceEnabled()) {
            log.trace("event: {}", e);
        }
        this.eventPublisher.publishEvent(e);

        return res;
    }

    private <I, R> D transition(
        @NotNull D dto,
        Function<Fsm<S, E, D>, Optional<R>> logic,
        @Nullable I input,
        @Nullable BiConsumer<D, R> effect
    ) {
        //execute update op with locking
        return exec(
            new EntityOperation<>(dto, EntityAction.UPDATE),
            d -> {
                try {
                    //build state machine on current context
                    //this will isolate the DTO from external modifications
                    Fsm<S, E, D> fsm = fsm(d);

                    //perform via FSM
                    Optional<R> output = logic.apply(fsm);
                    S state = fsm.getCurrentState();
                    D context = fsm.getContext();

                    //update status from fsm output
                    Map<String, Serializable> baseStatus = Map.of("state", state.name());

                    //merge action context into spec
                    //NOTE: we let transition fully modify metadata
                    d.setMetadata(context.getMetadata());

                    //merge action context into spec
                    //NOTE: we let transition fully modify spec
                    //TODO evaluate enforcing spec compliance and merging with old values
                    d.setSpec(context.getSpec());

                    //merge action context into status
                    //NOTE: we let transition fully modify status
                    d.setStatus(MapUtils.mergeMultipleMaps(context.getStatus(), baseStatus));

                    //let processors parse the result
                    if (metadataProcessorRegistry != null) {
                        Map<String, Serializable> psm = postProcess(d, state, input, metadataProcessorRegistry);

                        //merge metadata
                        d.setMetadata(MapUtils.mergeMultipleMaps(context.getMetadata(), psm));
                    }
                    if (specProcessorRegistry != null) {
                        Map<String, Serializable> psm = postProcess(d, state, input, specProcessorRegistry);

                        //merge spec
                        //TODO evaluate enforcing spec compliance and merging with old values
                        d.setSpec(MapUtils.mergeMultipleMaps(context.getSpec(), psm));
                    }
                    if (statusProcessorRegistry != null) {
                        Map<String, Serializable> psm = postProcess(d, state, input, statusProcessorRegistry);

                        //merge and enforce correct status
                        d.setStatus(MapUtils.mergeMultipleMaps(context.getStatus(), psm, baseStatus));
                    }

                    //side effect consumes output if available
                    if (effect != null) {
                        effect.accept(d, output.orElse(null));
                    }

                    return d;
                } catch (InvalidTransitionException e) {
                    log.debug("Invalid transition {} -> {}", e.getFromState(), e.getToState());
                    //TODO evaluate if we want to throw an exception here to avoid UPDATE on db
                    return d;
                } catch (RuntimeException ex) {
                    log.debug("Error performing transition on {}: {}", d.getId(), ex.getMessage());
                    throw new CoreRuntimeException(ex.getMessage());
                }
            }
        );
    }

    protected <I> Map<String, Serializable> postProcess(
        @NotNull D dto,
        @NotNull S state,
        @Nullable I input,
        @NotNull ProcessorRegistry<D, ? extends Spec> processorRegistry
    ) {
        String stage = "on" + StringUtils.capitalize(state.name().toLowerCase());
        // Iterate over all processor and store all RunBaseStatus as optional

        List<Map<String, Serializable>> res = processorRegistry
            .getProcessors(stage)
            .stream()
            .map(processor -> {
                try {
                    //deepclone dto to avoid side effects
                    D cd = JacksonMapper.deepClone(dto, typeClass);
                    Spec s = processor.process(stage, cd, input);
                    return s != null ? s.toMap() : null;
                } catch (IOException | RuntimeException e) {
                    log.error("Error processing stage {} for {}", stage, dto.getId(), e);
                    return null;
                }
            })
            .toList();

        Map<String, Serializable> map = res.stream().reduce(new HashMap<>(), MapUtils::mergeMultipleMaps);

        return map;
    }
}
