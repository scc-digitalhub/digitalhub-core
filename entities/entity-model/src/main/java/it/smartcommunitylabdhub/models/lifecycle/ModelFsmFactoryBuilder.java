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

package it.smartcommunitylabdhub.models.lifecycle;

import it.smartcommunitylabdhub.fsm.FsmState;
import it.smartcommunitylabdhub.lifecycle.BaseEntityStateBuilder;
import it.smartcommunitylabdhub.lifecycle.BaseFsmFactoryBuilder;
import it.smartcommunitylabdhub.models.Model;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

@Component
public class ModelFsmFactoryBuilder extends BaseFsmFactoryBuilder<Model> {

    public ModelFsmFactoryBuilder() {
        // Define the base FSM states and transitions
        builders(
            new BaseEntityStateBuilder<>(
                ModelState.CREATED.name(),
                Set.of(
                    Pair.of(ModelEvents.UPLOAD.name(), ModelState.UPLOADING.name()),
                    Pair.of(ModelEvents.READY.name(), ModelState.READY.name()),
                    Pair.of(ModelEvents.ERROR.name(), ModelState.ERROR.name()),
                    Pair.of(ModelEvents.DELETE.name(), ModelState.DELETED.name())
                )
            ),
            new BaseEntityStateBuilder<>(
                ModelState.UPLOADING.name(),
                Set.of(
                    Pair.of(ModelEvents.READY.name(), ModelState.READY.name()),
                    Pair.of(ModelEvents.ERROR.name(), ModelState.ERROR.name()),
                    Pair.of(ModelEvents.DELETE.name(), ModelState.DELETED.name())
                )
            ),
            new BaseEntityStateBuilder<>(
                ModelState.READY.name(),
                Set.of(
                    Pair.of(ModelEvents.READY.name(), ModelState.READY.name()),
                    Pair.of(ModelEvents.ERROR.name(), ModelState.ERROR.name()),
                    Pair.of(ModelEvents.DELETE.name(), ModelState.DELETED.name())
                )
            ),
            new BaseEntityStateBuilder<>(
                ModelState.ERROR.name(),
                Set.of(
                    Pair.of(ModelEvents.UPLOAD.name(), ModelState.UPLOADING.name()),
                    Pair.of(ModelEvents.READY.name(), ModelState.READY.name()),
                    Pair.of(ModelEvents.ERROR.name(), ModelState.ERROR.name()),
                    Pair.of(ModelEvents.DELETE.name(), ModelState.DELETED.name())
                )
            ),
            new BaseEntityStateBuilder<>(ModelState.DELETED.name(), Set.of())
        );
    }

    @Autowired(required = false)
    public void setBuilders(List<FsmState.Builder<String, String, Model>> builders) {
        //append any additional builders to the existing ones, the factory builder will merge states with the same name
        builders(builders);
    }
}
