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
import it.smartcommunitylabdhub.commons.models.metadata.MetadataDTO;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
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

    protected ProcessorRegistry<D> processorRegistry;
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

    @Autowired(required = false)
    public void setFsmFactory(Fsm.Factory<S, E, D> fsmFactory) {
        this.fsmFactory = fsmFactory;
    }

    @Autowired(required = false)
    public void setProcessorRegistry(ProcessorRegistry<D> processorRegistry) {
        this.processorRegistry = processorRegistry;
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
        D res = transition(dto, fsm -> fsm.perform(lifecycleEvent, input), input, effect);

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
        D res = transition(dto, fsm -> fsm.goToState(nextState, input), input, effect);

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

                    //merge action context into status
                    //NOTE: we let transition fully modify status
                    //TODO evaluate enforcing spec compliance and merging with old values
                    d.setStatus(MapUtils.mergeMultipleMaps(context.getStatus(), baseStatus));

                    //merge action context into spec
                    //NOTE: we let transition fully modify spec
                    //TODO evaluate enforcing spec compliance and merging with old values
                    d.setSpec(context.getSpec());

                    //merge action context into spec
                    //NOTE: we let transition fully modify metadata
                    //TODO evaluate enforcing spec compliance and merging with old values
                    d.setMetadata(context.getMetadata());

                    //let processors parse the result
                    if (processorRegistry != null) {
                        String stage = "on" + StringUtils.capitalize(state.name().toLowerCase());
                        // Iterate over all processor and store all RunBaseStatus as optional

                        List<? extends Spec> processorsStatus = processorRegistry
                            .getProcessors(stage)
                            .stream()
                            .map(processor -> {
                                try {
                                    //deepclone dto to avoid side effects
                                    D cd = JacksonMapper.deepClone(d, typeClass);
                                    return processor.process(stage, cd, input);
                                } catch (IOException | CoreRuntimeException e) {
                                    log.error("Error processing stage {} for {}", stage, dto.getId(), e);
                                    return null;
                                }
                            })
                            .filter(s -> s != null)
                            .toList();

                        Map<String, Serializable> psm = processorsStatus
                            .stream()
                            .map(Spec::toMap)
                            .reduce(new HashMap<>(), MapUtils::mergeMultipleMaps);

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
                }
            }
        );
    }
}
