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

package it.smartcommunitylabdhub.core.triggers.lifecycle;

import it.smartcommunitylabdhub.commons.infrastructure.TriggerRun;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerEvent;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerJob;
import it.smartcommunitylabdhub.core.fsm.AbstractFsmFactory;
import it.smartcommunitylabdhub.fsm.FsmState;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TriggerFsmFactory
    extends AbstractFsmFactory<State, TriggerEvent, TriggerContext, TriggerRun<? extends TriggerJob>> {

    public TriggerFsmFactory(
        List<FsmState.Builder<State, TriggerEvent, TriggerContext, TriggerRun<? extends TriggerJob>>> stateBuilders
    ) {
        super(stateBuilders);
    }
}
