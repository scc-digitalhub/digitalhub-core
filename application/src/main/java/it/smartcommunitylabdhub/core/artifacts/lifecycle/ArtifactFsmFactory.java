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

import it.smartcommunitylabdhub.commons.models.artifact.Artifact;
import it.smartcommunitylabdhub.lifecycle.BaseEntityState;
import it.smartcommunitylabdhub.lifecycle.BaseFsmFactory;
import java.util.Set;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

@Component
public class ArtifactFsmFactory extends BaseFsmFactory<Artifact, ArtifactState, ArtifactEvents> {

    public ArtifactFsmFactory() {
        super(
            new BaseEntityState<>(
                ArtifactState.CREATED,
                Set.of(
                    Pair.of(ArtifactEvents.UPLOAD, ArtifactState.UPLOADING),
                    Pair.of(ArtifactEvents.READY, ArtifactState.READY),
                    Pair.of(ArtifactEvents.ERROR, ArtifactState.ERROR),
                    Pair.of(ArtifactEvents.DELETE, ArtifactState.DELETED)
                )
            ),
            new BaseEntityState<>(
                ArtifactState.UPLOADING,
                Set.of(
                    Pair.of(ArtifactEvents.READY, ArtifactState.READY),
                    Pair.of(ArtifactEvents.ERROR, ArtifactState.ERROR),
                    Pair.of(ArtifactEvents.DELETE, ArtifactState.DELETED)
                )
            ),
            new BaseEntityState<>(
                ArtifactState.READY,
                Set.of(
                    Pair.of(ArtifactEvents.READY, ArtifactState.READY),
                    Pair.of(ArtifactEvents.ERROR, ArtifactState.ERROR),
                    Pair.of(ArtifactEvents.DELETE, ArtifactState.DELETED)
                )
            ),
            new BaseEntityState<>(
                ArtifactState.ERROR,
                Set.of(
                    Pair.of(ArtifactEvents.UPLOAD, ArtifactState.UPLOADING),
                    Pair.of(ArtifactEvents.READY, ArtifactState.READY),
                    Pair.of(ArtifactEvents.ERROR, ArtifactState.ERROR),
                    Pair.of(ArtifactEvents.DELETE, ArtifactState.DELETED)
                )
            ),
            new BaseEntityState<>(ArtifactState.DELETED, Set.of())
        );
    }
}
