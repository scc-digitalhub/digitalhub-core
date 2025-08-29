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

package it.smartcommunitylabdhub.core.models.lifecycle;

import it.smartcommunitylabdhub.commons.models.model.Model;
import it.smartcommunitylabdhub.lifecycle.BaseEntityState;
import it.smartcommunitylabdhub.lifecycle.BaseFsmFactory;

import java.util.Set;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

@Component
public class ModelFsmFactory extends BaseFsmFactory<Model, ModelState, ModelEvents> {

    public ModelFsmFactory() {
        super(
            new BaseEntityState<>(
                ModelState.CREATED,
                Set.of(
                    Pair.of(ModelEvents.UPLOAD, ModelState.UPLOADING),
                    Pair.of(ModelEvents.READY, ModelState.READY),
                    Pair.of(ModelEvents.ERROR, ModelState.ERROR),
                    Pair.of(ModelEvents.DELETE, ModelState.DELETED)
                )
            ),
            new BaseEntityState<>(
                ModelState.UPLOADING,
                Set.of(
                    Pair.of(ModelEvents.READY, ModelState.READY),
                    Pair.of(ModelEvents.ERROR, ModelState.ERROR),
                    Pair.of(ModelEvents.DELETE, ModelState.DELETED)
                )
            ),
            new BaseEntityState<>(
                ModelState.READY,
                Set.of(
                    Pair.of(ModelEvents.READY, ModelState.READY),
                    Pair.of(ModelEvents.ERROR, ModelState.ERROR),
                    Pair.of(ModelEvents.DELETE, ModelState.DELETED)
                )
            ),
            new BaseEntityState<>(
                ModelState.ERROR,
                Set.of(
                    Pair.of(ModelEvents.UPLOAD, ModelState.UPLOADING),
                    Pair.of(ModelEvents.READY, ModelState.READY),
                    Pair.of(ModelEvents.ERROR, ModelState.ERROR),
                    Pair.of(ModelEvents.DELETE, ModelState.DELETED)
                )
            ),
            new BaseEntityState<>(ModelState.DELETED, Set.of())
        );
    }
}
