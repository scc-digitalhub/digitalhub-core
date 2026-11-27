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

package it.smartcommunitylabdhub.containerimage.lifecycle;

import it.smartcommunitylabdhub.containerimage.ContainerImage;
import it.smartcommunitylabdhub.core.lifecycle.BaseEntityStateBuilder;
import it.smartcommunitylabdhub.core.lifecycle.BaseFsmFactory;
import it.smartcommunitylabdhub.core.lifecycle.BaseFsmFactoryBuilder;
import it.smartcommunitylabdhub.fsm.FsmState;
import it.smartcommunitylabdhub.fsm.Transition;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

@Component
public class ContainerImageFsmFactoryBuilder {

    private final BaseFsmFactoryBuilder<ContainerImage> builder;

    public ContainerImageFsmFactoryBuilder() {
        builder = new BaseFsmFactoryBuilder<>();
        // Define the base FSM states and transitions
        builder.builders(
            new BaseEntityStateBuilder<ContainerImage>(
                ContainerImageState.CREATED.name(),
                Set.of(
                    Pair.of(ContainerImageEvents.UPLOAD.name(), ContainerImageState.UPLOADING.name()),
                    Pair.of(ContainerImageEvents.READY.name(), ContainerImageState.READY.name()),
                    Pair.of(ContainerImageEvents.ERROR.name(), ContainerImageState.ERROR.name())
                )
            ).tx(toDeleting()),
            new BaseEntityStateBuilder<ContainerImage>(
                ContainerImageState.UPLOADING.name(),
                Set.of(
                    Pair.of(ContainerImageEvents.READY.name(), ContainerImageState.READY.name()),
                    Pair.of(ContainerImageEvents.ERROR.name(), ContainerImageState.ERROR.name())
                )
            ).tx(toDeleting()),
            new BaseEntityStateBuilder<ContainerImage>(
                ContainerImageState.READY.name(),
                Set.of(
                    Pair.of(ContainerImageEvents.UPDATE.name(), ContainerImageState.CREATED.name()),
                    Pair.of(ContainerImageEvents.READY.name(), ContainerImageState.READY.name()),
                    Pair.of(ContainerImageEvents.ERROR.name(), ContainerImageState.ERROR.name())
                )
            ).tx(toDeleting()),
            new BaseEntityStateBuilder<ContainerImage>(
                ContainerImageState.ERROR.name(),
                Set.of(
                    Pair.of(ContainerImageEvents.UPLOAD.name(), ContainerImageState.UPLOADING.name()),
                    Pair.of(ContainerImageEvents.READY.name(), ContainerImageState.READY.name()),
                    Pair.of(ContainerImageEvents.ERROR.name(), ContainerImageState.ERROR.name())
                )
            ).tx(toDeleting()),
            //cleanup (finalizers) runs as detached effects of DELETING, the caller concludes
            new BaseEntityStateBuilder<>(
                ContainerImageState.DELETING.name(),
                List.of(toDeleting(), to(ContainerImageState.DELETED.name()), to(ContainerImageState.ERROR.name()))
            ),
            new BaseEntityStateBuilder<>(ContainerImageState.DELETED.name(), Set.of())
        );
    }

    private static Transition<String, String, ContainerImage> toDeleting() {
        return new Transition.Builder<String, String, ContainerImage>()
            .event(ContainerImageEvents.DELETE.name())
            .nextState(ContainerImageState.DELETING.name())
            .detached()
            .build();
    }

    //no event: reachable only via handle
    private static Transition<String, String, ContainerImage> to(String state) {
        return new Transition.Builder<String, String, ContainerImage>().nextState(state).build();
    }

    @Autowired(required = false)
    public void setBuilders(List<FsmState.Builder<String, String, ContainerImage>> builders) {
        //append any additional builders to the existing ones, the factory builder will merge states with the same name
        builder.builders(builders);
    }

    public BaseFsmFactory<ContainerImage> build() {
        return builder.build();
    }
}
