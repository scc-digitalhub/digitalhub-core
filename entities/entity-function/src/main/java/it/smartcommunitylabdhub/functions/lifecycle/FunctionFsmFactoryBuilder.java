/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.functions.lifecycle;

import it.smartcommunitylabdhub.core.lifecycle.BaseEntityStateBuilder;
import it.smartcommunitylabdhub.core.lifecycle.BaseFsmFactory;
import it.smartcommunitylabdhub.core.lifecycle.BaseFsmFactoryBuilder;
import it.smartcommunitylabdhub.fsm.FsmState;
import it.smartcommunitylabdhub.fsm.Transition;
import it.smartcommunitylabdhub.functions.Function;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

/**
 * Function lifecycle: same states as artifacts, without UPLOADING (no files support yet)
 */
@Component
public class FunctionFsmFactoryBuilder {

    private final BaseFsmFactoryBuilder<Function> builder;

    public FunctionFsmFactoryBuilder() {
        builder = new BaseFsmFactoryBuilder<>();
        // Define the base FSM states and transitions
        builder.builders(
            new BaseEntityStateBuilder<Function>(
                FunctionState.CREATED.name(),
                Set.of(
                    Pair.of(FunctionEvents.READY.name(), FunctionState.READY.name()),
                    Pair.of(FunctionEvents.ERROR.name(), FunctionState.ERROR.name())
                )
            ).tx(toDeleting()),
            new BaseEntityStateBuilder<Function>(
                FunctionState.READY.name(),
                Set.of(
                    Pair.of(FunctionEvents.READY.name(), FunctionState.READY.name()),
                    Pair.of(FunctionEvents.ERROR.name(), FunctionState.ERROR.name())
                )
            ).tx(toDeleting()),
            new BaseEntityStateBuilder<Function>(
                FunctionState.ERROR.name(),
                Set.of(
                    Pair.of(FunctionEvents.READY.name(), FunctionState.READY.name()),
                    Pair.of(FunctionEvents.ERROR.name(), FunctionState.ERROR.name())
                )
            ).tx(toDeleting()),
            //cleanup (finalizers) runs as detached effects of DELETING, the caller concludes
            new BaseEntityStateBuilder<>(
                FunctionState.DELETING.name(),
                List.of(toDeleting(), to(FunctionState.DELETED.name()), to(FunctionState.ERROR.name()))
            ),
            new BaseEntityStateBuilder<>(FunctionState.DELETED.name(), Set.of())
        );
    }

    private static Transition<String, String, Function> toDeleting() {
        return new Transition.Builder<String, String, Function>()
            .event(FunctionEvents.DELETE.name())
            .nextState(FunctionState.DELETING.name())
            .detached()
            .build();
    }

    //no event: reachable only via handle
    private static Transition<String, String, Function> to(String state) {
        return new Transition.Builder<String, String, Function>().nextState(state).build();
    }

    @Autowired(required = false)
    public void setBuilders(List<FsmState.Builder<String, String, Function>> builders) {
        //append any additional builders to the existing ones, the factory builder will merge states with the same name
        builder.builders(builders);
    }

    public BaseFsmFactory<Function> build() {
        return builder.build();
    }
}
