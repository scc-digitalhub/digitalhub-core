/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package it.smartcommunitylabdhub.triggers.lifecycle;

import it.smartcommunitylabdhub.commons.models.trigger.Trigger;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.fsm.Transition;
import it.smartcommunitylabdhub.triggers.infrastructure.Actuator;
import it.smartcommunitylabdhub.triggers.models.TriggerRunBaseStatus;
import it.smartcommunitylabdhub.triggers.specs.TriggerBaseSpec;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TriggerStateStopped<X extends TriggerBaseSpec, Z extends TriggerRunBaseStatus>
    extends TriggerBaseState<TriggerState, TriggerEvent, X, Z> {

    public TriggerStateStopped(Actuator<X, ?, Z> actuator) {
        super(TriggerState.STOPPED, actuator);
        //transitions
        txs =
            List.of(
                //(RUN)->RUNNING
                new Transition.Builder<TriggerState, TriggerEvent, Trigger>()
                    .event(TriggerEvent.RUN)
                    .nextState(TriggerState.RUNNING)
                    .withInternalLogic((currentState, nextState, event, trigger, i) -> {
                        //runtime callback
                        Optional
                            .ofNullable(actuator.run(trigger))
                            .ifPresent(status ->
                                trigger.setStatus(MapUtils.mergeMultipleMaps(trigger.getStatus(), status.toMap()))
                            );

                        return Optional.empty();
                    })
                    .build(),
                //(ERROR)->ERROR
                new Transition.Builder<TriggerState, TriggerEvent, Trigger>()
                    .event(TriggerEvent.ERROR)
                    .nextState(TriggerState.ERROR)
                    .withInternalLogic((currentState, nextState, event, trigger, i) -> {
                        //no-op, nothing happened yet
                        return Optional.empty();
                    })
                    .build(),
                //(DELETE)->DELETED
                toDelete().build()
            );
    }
}
