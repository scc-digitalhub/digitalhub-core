/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.workflows.lifecycle;

import it.smartcommunitylabdhub.core.lifecycle.BaseEntityStateBuilder;
import it.smartcommunitylabdhub.core.lifecycle.BaseFsmFactory;
import it.smartcommunitylabdhub.core.lifecycle.BaseFsmFactoryBuilder;
import it.smartcommunitylabdhub.fsm.FsmState;
import it.smartcommunitylabdhub.fsm.Transition;
import it.smartcommunitylabdhub.workflows.Workflow;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

/**
 * Workflow lifecycle: same states as artifacts, without UPLOADING (no files support yet)
 */
@Component
public class WorkflowFsmFactoryBuilder {

    private final BaseFsmFactoryBuilder<Workflow> builder;

    public WorkflowFsmFactoryBuilder() {
        builder = new BaseFsmFactoryBuilder<>();
        // Define the base FSM states and transitions
        builder.builders(
            new BaseEntityStateBuilder<Workflow>(
                WorkflowState.CREATED.name(),
                Set.of(
                    Pair.of(WorkflowEvents.READY.name(), WorkflowState.READY.name()),
                    Pair.of(WorkflowEvents.ERROR.name(), WorkflowState.ERROR.name())
                )
            ).tx(toDeleting()),
            new BaseEntityStateBuilder<Workflow>(
                WorkflowState.READY.name(),
                Set.of(
                    Pair.of(WorkflowEvents.READY.name(), WorkflowState.READY.name()),
                    Pair.of(WorkflowEvents.ERROR.name(), WorkflowState.ERROR.name())
                )
            ).tx(toDeleting()),
            new BaseEntityStateBuilder<Workflow>(
                WorkflowState.ERROR.name(),
                Set.of(
                    Pair.of(WorkflowEvents.READY.name(), WorkflowState.READY.name()),
                    Pair.of(WorkflowEvents.ERROR.name(), WorkflowState.ERROR.name())
                )
            ).tx(toDeleting()),
            //cleanup (finalizers) runs as detached effects of DELETING, the caller concludes
            new BaseEntityStateBuilder<>(
                WorkflowState.DELETING.name(),
                List.of(toDeleting(), to(WorkflowState.DELETED.name()), to(WorkflowState.ERROR.name()))
            ),
            new BaseEntityStateBuilder<>(WorkflowState.DELETED.name(), Set.of())
        );
    }

    private static Transition<String, String, Workflow> toDeleting() {
        return new Transition.Builder<String, String, Workflow>()
            .event(WorkflowEvents.DELETE.name())
            .nextState(WorkflowState.DELETING.name())
            .detached()
            .build();
    }

    //no event: reachable only via handle
    private static Transition<String, String, Workflow> to(String state) {
        return new Transition.Builder<String, String, Workflow>().nextState(state).build();
    }

    @Autowired(required = false)
    public void setBuilders(List<FsmState.Builder<String, String, Workflow>> builders) {
        //append any additional builders to the existing ones, the factory builder will merge states with the same name
        builder.builders(builders);
    }

    public BaseFsmFactory<Workflow> build() {
        return builder.build();
    }
}
