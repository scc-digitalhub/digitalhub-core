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

import it.smartcommunitylabdhub.commons.lifecycle.LifecycleEvents;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.model.Model;
import it.smartcommunitylabdhub.core.lifecycle.BaseLifecycleManager;
import it.smartcommunitylabdhub.core.models.specs.ModelBaseStatus;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ModelLifecycleManager extends BaseLifecycleManager<Model, ModelBaseStatus> {

    /*
     * Actions: ask to perform
     */

    public Model upload(@NotNull Model model) {
        return perform(model, LifecycleEvents.UPLOAD.name());
    }

    public Model delete(@NotNull Model model) {
        return perform(model, LifecycleEvents.DELETE.name());
    }

    public Model update(@NotNull Model model) {
        return perform(model, LifecycleEvents.UPDATE.name());
    }

    /*
     * Events: callbacks
     */

    //NOTE: disabled, we can not handle onCreated because there is no state change
    // public Model onCreated(@NotNull Model model) {
    //     return handle(model, State.CREATED);
    // }

    public Model onUploading(@NotNull Model model) {
        return handle(model, State.UPLOADING.name());
    }

    public Model onReady(@NotNull Model model) {
        return handle(model, State.READY.name());
    }

    public Model onError(@NotNull Model model) {
        return handle(model, State.ERROR.name());
    }
}
