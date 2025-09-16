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

package it.smartcommunitylabdhub.triggers.lifecycle;

import it.smartcommunitylabdhub.commons.lifecycle.LifecycleEvents;
import it.smartcommunitylabdhub.commons.lifecycle.LifecycleState;
import it.smartcommunitylabdhub.commons.models.trigger.Trigger;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.fsm.FsmState;
import it.smartcommunitylabdhub.fsm.Transition;
import it.smartcommunitylabdhub.triggers.infrastructure.Actuator;
import it.smartcommunitylabdhub.triggers.models.TriggerRunBaseStatus;
import it.smartcommunitylabdhub.triggers.specs.TriggerBaseSpec;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

@Slf4j
public class TriggerBaseState<
    S extends Enum<S> & LifecycleState<Trigger>,
    E extends Enum<E> & LifecycleEvents<Trigger>,
    X extends TriggerBaseSpec,
    Z extends TriggerRunBaseStatus
>
    implements FsmState.Builder<S, E, Trigger> {

    protected final Class<S> stateClass;
    protected final Class<E> eventsClass;

    protected final S state;
    protected final Actuator<X, ?, Z> actuator;

    protected List<Transition<S, E, Trigger>> txs;

    @SuppressWarnings("unchecked")
    public TriggerBaseState(S state, Actuator<X, ?, Z> actuator) {
        Assert.notNull(state, "state is required");
        Assert.notNull(actuator, "actuator is required");

        this.state = state;
        this.actuator = actuator;

        // resolve generics type via subclass trick
        Type ts = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.stateClass = (Class<S>) ts;
        Type te = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[1];
        this.eventsClass = (Class<E>) te;
    }

    public FsmState<S, E, Trigger> build() {
        return new FsmState<>(state, txs);
    }

    protected Transition.Builder<S, E, Trigger> toDelete() {
        //(DELETE)->DELETED
        return new Transition.Builder<S, E, Trigger>()
            .event(Enum.valueOf(eventsClass, TriggerEvent.DELETE.name()))
            .nextState(Enum.valueOf(stateClass, TriggerState.DELETED.name()))
            .withInternalLogic((currentState, nextState, event, trigger, i) -> {
                //runtime callback for stop
                Optional
                    .ofNullable(actuator.stop(trigger))
                    .ifPresent(status ->
                        trigger.setStatus(MapUtils.mergeMultipleMaps(trigger.getStatus(), status.toMap()))
                    );

                return Optional.empty();
            });
    }
}
