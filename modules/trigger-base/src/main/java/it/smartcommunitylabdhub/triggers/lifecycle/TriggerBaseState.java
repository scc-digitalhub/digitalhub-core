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

import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.fsm.FsmState;
import it.smartcommunitylabdhub.fsm.Transition;
import it.smartcommunitylabdhub.triggers.Trigger;
import it.smartcommunitylabdhub.triggers.infrastructure.Actuator;
import it.smartcommunitylabdhub.triggers.models.TriggerRunBaseStatus;
import it.smartcommunitylabdhub.triggers.specs.TriggerBaseSpec;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

@Slf4j
public class TriggerBaseState<X extends TriggerBaseSpec, Z extends TriggerRunBaseStatus>
    implements FsmState.Builder<String, String, Trigger> {

    protected final String state;
    protected final Actuator<X, ?, Z> actuator;

    protected List<Transition<String, String, Trigger>> txs;

    public TriggerBaseState(String state, Actuator<X, ?, Z> actuator) {
        Assert.notNull(state, "state is required");
        Assert.notNull(actuator, "actuator is required");

        this.state = state;
        this.actuator = actuator;
    }

    public FsmState<String, String, Trigger> build() {
        return new FsmState<>(state, txs);
    }

    protected Transition.Builder<String, String, Trigger> toDelete() {
        //(DELETE)->DELETED
        return new Transition.Builder<String, String, Trigger>()
            .event(TriggerEvent.DELETE.name())
            .nextState(TriggerState.DELETED.name())
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
