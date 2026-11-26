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
 * CREATED   -> BUILT [BUILD], ERROR, DELETED [DELETE]
 * BUILT     -> READY [RUN], ERROR, DELETED [DELETE]
 * READY     -> RUNNING, ERROR, DELETED [DELETE]
 * RUNNING   -> RUNNING (loop), COMPLETED, ERROR, DELETED [DELETE]
 * COMPLETED -> DELETED [DELETE]
 * ERROR     -> DELETED [DELETE]
 *
 * Transition logic is pluggable per target state, every executed transition is recorded.
 */
public class TestFsm {

    public interface Logic extends TransitionLogic<String, String, TestDTO, Serializable, Serializable> {}

    //executed transitions as "FROM>TO"
    private final List<String> calls = new CopyOnWriteArrayList<>();
    //custom logic by target state
    private final Map<String, Logic> logic = new ConcurrentHashMap<>();

    public List<String> calls() {
        return calls;
    }

    public void on(String nextState, Logic l) {
        logic.put(nextState, l);
    }

    public BaseFsmFactory<TestDTO> factory() {
        List<FsmState.Builder<String, String, TestDTO>> builders = new ArrayList<>();
        builders.add(state("CREATED", tx("BUILT", "BUILD"), tx("ERROR", null), tx("DELETED", "DELETE")));
        builders.add(state("BUILT", tx("READY", "RUN"), tx("ERROR", null), tx("DELETED", "DELETE")));
        builders.add(state("READY", tx("RUNNING", null), tx("ERROR", null), tx("DELETED", "DELETE")));
        builders.add(
            state("RUNNING", tx("RUNNING", null), tx("COMPLETED", null), tx("ERROR", null), tx("DELETED", "DELETE"))
        );
        builders.add(state("COMPLETED", tx("DELETED", "DELETE")));
        builders.add(state("ERROR", tx("DELETED", "DELETE")));
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
        return new Transition.Builder<String, String, TestDTO>()
            .event(event)
            .nextState(next)
            .<Serializable, Serializable>withInternalLogic((cur, nxt, ev, ctx, input) -> {
                calls.add(cur + ">" + nxt);
                Logic l = logic.get(nxt);
                return l != null ? l.apply(cur, nxt, ev, ctx, input) : Optional.empty();
            })
            .build();
    }
}
