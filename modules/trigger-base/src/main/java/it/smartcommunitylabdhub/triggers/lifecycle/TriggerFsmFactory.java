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
import it.smartcommunitylabdhub.lifecycle.BaseFsmFactory;
import it.smartcommunitylabdhub.triggers.infrastructure.Actuator;
import it.smartcommunitylabdhub.triggers.models.TriggerRunBaseStatus;
import it.smartcommunitylabdhub.triggers.specs.TriggerBaseSpec;
import it.smartcommunitylabdhub.triggers.specs.TriggerBaseStatus;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TriggerFsmFactory<S extends TriggerBaseSpec, Z extends TriggerBaseStatus, R extends TriggerRunBaseStatus>
    extends BaseFsmFactory<Trigger, TriggerState, TriggerEvent> {

    public TriggerFsmFactory(Actuator<S, Z, R> actuator) {
        super(
            new TriggerStateCreated<>(actuator),
            new TriggerStateRunning<>(actuator),
            new TriggerStateError<>(actuator),
            new TriggerStateStopped<>(actuator)
        );
    }
}
