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

package it.smartcommunitylabdhub.core.dataitems.lifecycle;

import it.smartcommunitylabdhub.commons.lifecycle.LifecycleEvents;
import it.smartcommunitylabdhub.commons.models.dataitem.DataItem;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.core.dataitems.specs.DataItemBaseStatus;
import it.smartcommunitylabdhub.core.lifecycle.BaseLifecycleManager;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DataItemLifecycleManager extends BaseLifecycleManager<DataItem, DataItemBaseStatus> {

    /*
     * Actions: ask to perform
     */

    public DataItem upload(@NotNull DataItem dataItem) {
        return perform(dataItem, LifecycleEvents.UPLOAD.name());
    }

    public DataItem delete(@NotNull DataItem dataItem) {
        return perform(dataItem, LifecycleEvents.DELETE.name());
    }

    public DataItem update(@NotNull DataItem dataItem) {
        return perform(dataItem, LifecycleEvents.UPDATE.name());
    }

    /*
     * Events: callbacks
     */

    //NOTE: disabled, we can not handle onCreated because there is no state change
    // public DataItem onCreated(@NotNull DataItem dataItem) {
    //     return handle(dataItem, State.CREATED);
    // }

    public DataItem onUploading(@NotNull DataItem dataItem) {
        return handle(dataItem, State.UPLOADING.name());
    }

    public DataItem onReady(@NotNull DataItem dataItem) {
        return handle(dataItem, State.READY.name());
    }

    public DataItem onError(@NotNull DataItem dataItem) {
        return handle(dataItem, State.ERROR.name());
    }
}
