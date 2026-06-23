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

package it.smartcommunitylabdhub.folder.lifecycle;

import it.smartcommunitylabdhub.core.lifecycle.BaseEntityStateBuilder;
import it.smartcommunitylabdhub.core.lifecycle.BaseFsmFactory;
import it.smartcommunitylabdhub.core.lifecycle.BaseFsmFactoryBuilder;
import it.smartcommunitylabdhub.folder.Folder;
import it.smartcommunitylabdhub.fsm.FsmState;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

@Component
public class FolderFsmFactoryBuilder {

    private final BaseFsmFactoryBuilder<Folder> builder;

    public FolderFsmFactoryBuilder() {
        builder = new BaseFsmFactoryBuilder<>();
        // Define the base FSM states and transitions
        builder.builders(
            new BaseEntityStateBuilder<>(
                FolderState.READY.name(),
                Set.of(
                    Pair.of(FolderEvents.UPDATE.name(), FolderState.READY.name()),
                    Pair.of(FolderEvents.ERROR.name(), FolderState.ERROR.name()),
                    Pair.of(FolderEvents.DELETE.name(), FolderState.DELETED.name())
                )
            ),
            new BaseEntityStateBuilder<>(
                FolderState.ERROR.name(),
                Set.of(
                    Pair.of(FolderEvents.UPDATE.name(), FolderState.READY.name()),
                    Pair.of(FolderEvents.ERROR.name(), FolderState.ERROR.name()),
                    Pair.of(FolderEvents.DELETE.name(), FolderState.DELETED.name())
                )
            ),
            new BaseEntityStateBuilder<>(FolderState.DELETED.name(), Set.of())
        );
    }

    @Autowired(required = false)
    public void setBuilders(List<FsmState.Builder<String, String, Folder>> builders) {
        //append any additional builders to the existing ones, the factory builder will merge states with the same name
        builder.builders(builders);
    }

    public BaseFsmFactory<Folder> build() {
        return builder.build();
    }
}
