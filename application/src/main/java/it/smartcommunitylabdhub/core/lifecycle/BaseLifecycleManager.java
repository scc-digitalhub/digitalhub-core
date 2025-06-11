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

package it.smartcommunitylabdhub.core.lifecycle;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.lifecycle.LifecycleEvent;
import it.smartcommunitylabdhub.commons.lifecycle.LifecycleEvents;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.core.components.run.LifecycleManager;
import it.smartcommunitylabdhub.core.events.EntityAction;
import it.smartcommunitylabdhub.core.events.EntityOperation;
import it.smartcommunitylabdhub.core.persistence.BaseEntity;
import it.smartcommunitylabdhub.fsm.Fsm;
import it.smartcommunitylabdhub.fsm.exceptions.InvalidTransitionException;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public abstract class BaseLifecycleManager<D extends BaseDTO & StatusDTO, E extends BaseEntity, T extends Spec>
    extends LifecycleManager<D, E> {

    private Fsm.Factory<State, LifecycleEvents, LifecycleContext<D>, LifecycleEvent<D>> fsmFactory;

    @Autowired(required = false)
    public void setFsmFactory(Fsm.Factory<State, LifecycleEvents, LifecycleContext<D>, LifecycleEvent<D>> fsmFactory) {
        this.fsmFactory = fsmFactory;
    }

    protected Fsm<State, LifecycleEvents, LifecycleContext<D>, LifecycleEvent<D>> fsm(D dto) {
        if (fsmFactory == null) {
            throw new IllegalStateException("FSM factory not set: provide or override");
        }

        //initial state is current state
        String state = StatusFieldAccessor.with(dto.getStatus()).getState();
        if (state == null) {
            throw new IllegalStateException("State not set");
        }

        //get enum via enum..
        State initialState = State.valueOf(state);

        //default context has only dto in it
        LifecycleContext<D> context = new LifecycleContext<>(dto);

        // create state machine via factory
        return fsmFactory.create(initialState, context);
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
    public D perform(@NotNull D dto, @NotNull LifecycleEvents event) {
        //build synthetic input from event
        LifecycleEvent<D> input = new LifecycleEvent<>();
        input.setId(dto.getId());
        input.setKind(dto.getKind());
        input.setUser(dto.getUser());
        input.setProject(dto.getProject());
        input.setEvent(event);

        return perform(dto, event, input, null);
    }

    public D perform(@NotNull D dto, @NotNull LifecycleEvents event, @Nullable LifecycleEvent<D> input) {
        return perform(dto, event, input, null);
    }

    public D perform(
        @NotNull D dto,
        @NotNull LifecycleEvents event,
        @Nullable LifecycleEvent<D> input,
        @Nullable BiConsumer<D, T> effect
    ) {
        log.debug("perform {} for {} with id {}", event, dto.getClass().getSimpleName().toLowerCase(), dto.getId());
        if (log.isTraceEnabled()) {
            log.trace("dto: {}", dto);
        }

        // build state machine on current context
        Fsm<State, LifecycleEvents, LifecycleContext<D>, LifecycleEvent<D>> fsm = fsm(dto);

        //execute update op with locking
        dto =
            exec(
                new EntityOperation<>(dto, EntityAction.UPDATE),
                d -> {
                    try {
                        //perform via FSM
                        Optional<T> output = fsm.perform(event, input);
                        State state = fsm.getCurrentState();

                        //update status from fsm output
                        Map<String, Serializable> baseStatus = Map.of("state", state.name());

                        //merge action output into status
                        d.setStatus(
                            MapUtils.mergeMultipleMaps(
                                d.getStatus(),
                                output.isPresent() ? output.get().toMap() : null,
                                baseStatus
                            )
                        );

                        //side effect
                        if (effect != null) {
                            effect.accept(d, output.orElse(null));
                        }

                        return d;
                    } catch (InvalidTransitionException e) {
                        log.debug("Invalid transition {} -> {}", e.getFromState(), e.getToState());
                        return d;
                    }
                }
            );

        //publish new event
        LifecycleEvent<D> e = new LifecycleEvent<>();
        e.setId(dto.getId());
        e.setKind(dto.getKind());
        e.setUser(dto.getUser());
        e.setProject(dto.getProject());
        e.setEvent(event);
        e.setState(fsm.getCurrentState());
        //append object to event
        e.setDto(dto);

        log.debug("publish event {} for {}", event, dto.getId());
        if (log.isTraceEnabled()) {
            log.trace("event: {}", e);
        }
        this.eventPublisher.publishEvent(e);

        return dto;
    }

    /*
     * Handle lifecycle events from state changes
     */
    public D handle(@NotNull D dto, State nexState) {
        //build synthetic input from state change
        LifecycleEvent<D> input = new LifecycleEvent<>();
        input.setId(dto.getId());
        input.setKind(dto.getKind());
        input.setUser(dto.getUser());
        input.setProject(dto.getProject());
        input.setState(nexState);

        return handle(dto, nexState, input, null);
    }

    public D handle(@NotNull D dto, State nexState, @Nullable LifecycleEvent<D> input) {
        return handle(dto, nexState, input, null);
    }

    public D handle(
        @NotNull D dto,
        State nextState,
        @Nullable LifecycleEvent<D> input,
        @Nullable BiConsumer<D, T> effect
    ) {
        log.debug("handle {} for {} with id {}", nextState, dto.getClass().getSimpleName().toLowerCase(), dto.getId());
        if (log.isTraceEnabled()) {
            log.trace("dto: {}", dto);
        }

        // build state machine on current context
        Fsm<State, LifecycleEvents, LifecycleContext<D>, LifecycleEvent<D>> fsm = fsm(dto);

        //execute update op with locking
        dto =
            exec(
                new EntityOperation<>(dto, EntityAction.UPDATE),
                d -> {
                    try {
                        //perform via FSM
                        Optional<T> output = fsm.goToState(nextState, input);
                        State state = fsm.getCurrentState();

                        //update status from fsm output
                        Map<String, Serializable> baseStatus = Map.of("state", state.name());

                        //merge action output into status
                        d.setStatus(
                            MapUtils.mergeMultipleMaps(
                                d.getStatus(),
                                output.isPresent() ? output.get().toMap() : null,
                                baseStatus
                            )
                        );

                        //side effect
                        if (effect != null) {
                            effect.accept(d, output.orElse(null));
                        }

                        return d;
                    } catch (InvalidTransitionException e) {
                        log.debug("Invalid transition {} -> {}", e.getFromState(), e.getToState());
                        return d;
                    }
                }
            );

        //publish new event
        LifecycleEvent<D> e = LifecycleEvent
            .<D>builder()
            .id(dto.getId())
            .kind(dto.getKind())
            .user(dto.getUser())
            .project(dto.getProject())
            .state(fsm.getCurrentState())
            //append object to event
            .dto(dto)
            .build();

        log.debug("publish event on state {} for {}", nextState, dto.getId());
        if (log.isTraceEnabled()) {
            log.trace("event: {}", e);
        }
        this.eventPublisher.publishEvent(e);

        return dto;
    }
}
