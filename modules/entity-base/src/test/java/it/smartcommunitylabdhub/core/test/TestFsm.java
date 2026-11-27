/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.core.test;

import it.smartcommunitylabdhub.core.lifecycle.BaseFsmFactory;
import it.smartcommunitylabdhub.fsm.FsmState;
import it.smartcommunitylabdhub.fsm.Transition;
import it.smartcommunitylabdhub.fsm.TransitionLogic;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test FSM, modeled after runs:
 *
 * CREATED   -> BUILT [BUILD], ERROR, DELETED [DELETE], DELETING [REMOVE]
 * BUILT     -> READY [RUN], ERROR, DELETED [DELETE]
 * READY     -> RUNNING, ERROR, DELETED [DELETE]
 * RUNNING   -> RUNNING (loop), COMPLETED, ERROR, DELETED [DELETE]
 * COMPLETED -> DELETED [DELETE]
 * ERROR     -> DELETED [DELETE], DELETING [REMOVE]
 * DELETING  -> DELETING [REMOVE] (loop, optional), DELETED, ERROR
 *
 * Transitions into DELETING are DETACHED.
 * Transition logic is pluggable per target state, every executed transition is recorded.
 */
public class TestFsm {

    public interface Logic extends TransitionLogic<String, String, TestDTO, Serializable, Serializable> {}

    //executed transitions as "FROM>TO"
    private final List<String> calls = new CopyOnWriteArrayList<>();
    //custom logic by target state
    private final Map<String, Logic> logic = new ConcurrentHashMap<>();
    //DELETING self loop, decided when building the factory
    private boolean deletingLoop = true;

    public List<String> calls() {
        return calls;
    }

    public void on(String nextState, Logic l) {
        logic.put(nextState, l);
    }

    public void noDeletingLoop() {
        this.deletingLoop = false;
    }

    public BaseFsmFactory<TestDTO> factory() {
        List<FsmState.Builder<String, String, TestDTO>> builders = new ArrayList<>();
        builders.add(
            state("CREATED", tx("BUILT", "BUILD"), tx("ERROR", null), tx("DELETED", "DELETE"), tx("DELETING", "REMOVE"))
        );
        builders.add(state("BUILT", tx("READY", "RUN"), tx("ERROR", null), tx("DELETED", "DELETE")));
        builders.add(state("READY", tx("RUNNING", null), tx("ERROR", null), tx("DELETED", "DELETE")));
        builders.add(
            state("RUNNING", tx("RUNNING", null), tx("COMPLETED", null), tx("ERROR", null), tx("DELETED", "DELETE"))
        );
        builders.add(state("COMPLETED", tx("DELETED", "DELETE")));
        builders.add(state("ERROR", tx("DELETED", "DELETE"), tx("DELETING", "REMOVE")));
        if (deletingLoop) {
            builders.add(state("DELETING", tx("DELETING", "REMOVE"), tx("DELETED", null), tx("ERROR", null)));
        } else {
            builders.add(state("DELETING", tx("DELETED", null), tx("ERROR", null)));
        }
        builders.add(state("DELETED"));

        return new BaseFsmFactory<TestDTO>(builders) {};
    }

    @SafeVarargs
    private static FsmState.Builder<String, String, TestDTO> state(
        String state,
        Transition<String, String, TestDTO>... txs
    ) {
        return () -> new FsmState<>(state, List.of(txs));
    }

    private Transition<String, String, TestDTO> tx(String next, String event) {
        Transition.Builder<String, String, TestDTO> builder = new Transition.Builder<String, String, TestDTO>()
            .event(event)
            .nextState(next)
            .<Serializable, Serializable>withInternalLogic((cur, nxt, ev, ctx, input) -> {
                calls.add(cur + ">" + nxt);
                Logic l = logic.get(nxt);
                return l != null ? l.apply(cur, nxt, ev, ctx, input) : Optional.empty();
            });

        if ("DELETING".equals(next)) {
            builder.detached();
        }

        return builder.build();
    }
}
