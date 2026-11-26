/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.fsm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FsmPathTest {

    /*
     * Graph modeled after runs:
     * CREATED -> BUILT, READY, ERROR
     * BUILT -> READY, ERROR
     * READY -> RUNNING, ERROR
     * RUNNING -> RUNNING (loop), COMPLETED, ERROR
     * COMPLETED -> DELETING ; ERROR -> DELETING ; DELETING -> DELETED
     * ISLAND (no incoming edges)
     */
    private static final Map<String, List<String>> GRAPH = Map.of(
        "CREATED",
        List.of("BUILT", "READY", "ERROR"),
        "BUILT",
        List.of("READY", "ERROR"),
        "READY",
        List.of("RUNNING", "ERROR"),
        "RUNNING",
        List.of("RUNNING", "COMPLETED", "ERROR"),
        "COMPLETED",
        List.of("DELETING"),
        "ERROR",
        List.of("DELETING"),
        "DELETING",
        List.of("DELETED"),
        "DELETED",
        List.of(),
        "ISLAND",
        List.of()
    );

    private static Fsm<String, String, Object> fsm(String initial, Map<String, List<String>> graph) {
        Fsm.Builder<String, String, Object> builder = new Fsm.Builder<>(initial, new Object());
        graph.forEach((state, nexts) -> {
            List<Transition<String, String, Object>> txs = new ArrayList<>();
            nexts.forEach(n -> txs.add(new Transition.Builder<String, String, Object>().nextState(n).build()));
            builder.withState(state, new FsmState<>(state, txs));
        });
        return builder.build();
    }

    @Test
    void findsShortestPath() {
        //direct edge CREATED -> READY wins over CREATED -> BUILT -> READY
        assertThat(fsm("CREATED", GRAPH).pathTo("RUNNING")).contains(List.of("READY", "RUNNING"));
        assertThat(fsm("BUILT", GRAPH).pathTo("COMPLETED")).contains(List.of("READY", "RUNNING", "COMPLETED"));
    }

    @Test
    void singleStepPath() {
        assertThat(fsm("CREATED", GRAPH).pathTo("BUILT")).contains(List.of("BUILT"));
        assertThat(fsm("DELETING", GRAPH).pathTo("DELETED")).contains(List.of("DELETED"));
    }

    @Test
    void sameStateRequiresExplicitCycle() {
        //explicit self loop: one step
        assertThat(fsm("RUNNING", GRAPH).pathTo("RUNNING")).contains(List.of("RUNNING"));
        //no cycle back to CREATED: no path
        assertThat(fsm("CREATED", GRAPH).pathTo("CREATED")).isEmpty();
    }

    @Test
    void unreachableOrUnknownTargetHasNoPath() {
        assertThat(fsm("COMPLETED", GRAPH).pathTo("RUNNING")).isEmpty();
        assertThat(fsm("CREATED", GRAPH).pathTo("ISLAND")).isEmpty();
        assertThat(fsm("CREATED", GRAPH).pathTo("UNKNOWN")).isEmpty();
        assertThat(fsm("DELETED", GRAPH).pathTo("ERROR")).isEmpty();
    }

    @Test
    void tieBreakFollowsDeclarationOrder() {
        //two shortest paths A -> X -> Z and A -> Y -> Z: the first declared wins
        Map<String, List<String>> graph = Map.of(
            "A",
            Arrays.asList("X", "Y"),
            "X",
            List.of("Z"),
            "Y",
            List.of("Z"),
            "Z",
            List.of()
        );
        assertThat(fsm("A", graph).pathTo("Z")).contains(List.of("X", "Z"));

        Map<String, List<String>> reversed = Map.of(
            "A",
            Arrays.asList("Y", "X"),
            "X",
            List.of("Z"),
            "Y",
            List.of("Z"),
            "Z",
            List.of()
        );
        assertThat(fsm("A", reversed).pathTo("Z")).contains(List.of("Y", "Z"));
    }

    @Test
    void cycleBackToCurrentState() {
        Map<String, List<String>> graph = Map.of("A", List.of("B"), "B", List.of("A"));
        assertThat(fsm("A", graph).pathTo("A")).contains(List.of("B", "A"));
    }

    @Test
    void planningDoesNotChangeState() {
        Fsm<String, String, Object> fsm = fsm("CREATED", GRAPH);
        fsm.pathTo("COMPLETED");
        assertThat(fsm.getCurrentState()).isEqualTo("CREATED");
    }
}
