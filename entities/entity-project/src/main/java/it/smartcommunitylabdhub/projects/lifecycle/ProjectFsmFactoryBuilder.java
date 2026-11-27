/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.projects.lifecycle;

import it.smartcommunitylabdhub.core.lifecycle.BaseEntityStateBuilder;
import it.smartcommunitylabdhub.core.lifecycle.BaseFsmFactory;
import it.smartcommunitylabdhub.core.lifecycle.BaseFsmFactoryBuilder;
import it.smartcommunitylabdhub.fsm.FsmState;
import it.smartcommunitylabdhub.fsm.Transition;
import it.smartcommunitylabdhub.projects.Project;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

/**
 * Project lifecycle: same states as artifacts, without UPLOADING (no files support yet)
 */
@Component
public class ProjectFsmFactoryBuilder {

    private final BaseFsmFactoryBuilder<Project> builder;

    public ProjectFsmFactoryBuilder() {
        builder = new BaseFsmFactoryBuilder<>();
        // Define the base FSM states and transitions
        builder.builders(
            new BaseEntityStateBuilder<Project>(
                ProjectState.CREATED.name(),
                Set.of(
                    Pair.of(ProjectEvents.READY.name(), ProjectState.READY.name()),
                    Pair.of(ProjectEvents.ERROR.name(), ProjectState.ERROR.name())
                )
            ).tx(toDeleting()),
            new BaseEntityStateBuilder<Project>(
                ProjectState.READY.name(),
                Set.of(
                    Pair.of(ProjectEvents.READY.name(), ProjectState.READY.name()),
                    Pair.of(ProjectEvents.ERROR.name(), ProjectState.ERROR.name())
                )
            ).tx(toDeleting()),
            new BaseEntityStateBuilder<Project>(
                ProjectState.ERROR.name(),
                Set.of(
                    Pair.of(ProjectEvents.READY.name(), ProjectState.READY.name()),
                    Pair.of(ProjectEvents.ERROR.name(), ProjectState.ERROR.name())
                )
            ).tx(toDeleting()),
            //cleanup (finalizers) runs as detached effects of DELETING, the caller concludes
            new BaseEntityStateBuilder<>(
                ProjectState.DELETING.name(),
                List.of(toDeleting(), to(ProjectState.DELETED.name()), to(ProjectState.ERROR.name()))
            ),
            new BaseEntityStateBuilder<>(ProjectState.DELETED.name(), Set.of())
        );
    }

    private static Transition<String, String, Project> toDeleting() {
        return new Transition.Builder<String, String, Project>()
            .event(ProjectEvents.DELETE.name())
            .nextState(ProjectState.DELETING.name())
            .detached()
            .build();
    }

    //no event: reachable only via handle
    private static Transition<String, String, Project> to(String state) {
        return new Transition.Builder<String, String, Project>().nextState(state).build();
    }

    @Autowired(required = false)
    public void setBuilders(List<FsmState.Builder<String, String, Project>> builders) {
        //append any additional builders to the existing ones, the factory builder will merge states with the same name
        builder.builders(builders);
    }

    public BaseFsmFactory<Project> build() {
        return builder.build();
    }
}
