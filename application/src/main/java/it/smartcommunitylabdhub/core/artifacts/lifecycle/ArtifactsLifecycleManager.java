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

package it.smartcommunitylabdhub.core.artifacts.lifecycle;

import it.smartcommunitylabdhub.commons.lifecycle.LifecycleEvents;
import it.smartcommunitylabdhub.commons.models.artifact.Artifact;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.core.artifacts.specs.ArtifactBaseStatus;
import it.smartcommunitylabdhub.core.lifecycle.BaseLifecycleManager;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ArtifactsLifecycleManager extends BaseLifecycleManager<Artifact, ArtifactBaseStatus> {

    /*
     * Actions: ask to perform
     */

    public Artifact upload(@NotNull Artifact artifact) {
        return perform(artifact, LifecycleEvents.UPLOAD.name());
    }

    public Artifact delete(@NotNull Artifact artifact) {
        return perform(artifact, LifecycleEvents.DELETE.name());
    }

    public Artifact update(@NotNull Artifact artifact) {
        return perform(artifact, LifecycleEvents.UPDATE.name());
    }

    /*
     * Events: callbacks
     */

    //NOTE: disabled, we can not handle onCreated because there is no state change
    // public Artifact onCreated(@NotNull Artifact artifact) {
    //     return handle(artifact, State.CREATED);
    // }

    public Artifact onUploading(@NotNull Artifact artifact) {
        return handle(artifact, State.UPLOADING.name());
    }

    public Artifact onReady(@NotNull Artifact artifact) {
        return handle(artifact, State.READY.name());
    }

    public Artifact onError(@NotNull Artifact artifact) {
        return handle(artifact, State.ERROR.name());
    }
}
