/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.core.services;

import static org.assertj.core.api.Assertions.assertThat;

import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Effect;
import it.smartcommunitylabdhub.core.test.TestDTO;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Finalizer driver: "onDeleting" effects, in order, best effort
 */
class BaseEntityFinalizerTest {

    private static TestDTO dto() {
        return TestDTO.builder()
            .id("e1")
            .name("n-e1")
            .kind("test")
            .project("prj")
            .spec(new HashMap<>(Map.of("a", 1)))
            .status(new HashMap<>(Map.of("state", "DELETED")))
            .build();
    }

    private static BaseEntityFinalizer<TestDTO> finalizer(Map<String, List<Effect<TestDTO>>> effects) {
        BaseEntityFinalizer<TestDTO> finalizer = new BaseEntityFinalizer<TestDTO>() {};
        finalizer.setEffectRegistry(stage -> effects.getOrDefault(stage, List.of()));
        return finalizer;
    }

    @Test
    void runsTheOnDeletingEffectsInOrder() {
        List<String> calls = new ArrayList<>();
        BaseEntityFinalizer<TestDTO> finalizer = finalizer(
            Map.of(
                "onDeleting",
                List.of(
                    (stage, d, out) -> calls.add("first:" + stage + ":" + d.getId()),
                    (stage, d, out) -> calls.add("second:" + stage + ":" + d.getId())
                ),
                "onDeleted",
                List.of((stage, d, out) -> calls.add("other stage"))
            )
        );

        finalizer.finalize(dto());

        assertThat(calls).containsExactly("first:onDeleting:e1", "second:onDeleting:e1");
    }

    @Test
    void everyEffectRunsEvenWhenOthersFail() {
        List<String> calls = new ArrayList<>();
        BaseEntityFinalizer<TestDTO> finalizer = finalizer(
            Map.of(
                "onDeleting",
                List.of(
                    (stage, d, out) -> {
                        throw new CoreRuntimeException("explicit failure");
                    },
                    (stage, d, out) -> {
                        throw new IllegalStateException("unexpected failure");
                    },
                    (stage, d, out) -> calls.add("last")
                )
            )
        );

        //best effort: nothing is thrown back
        finalizer.finalize(dto());

        assertThat(calls).containsExactly("last");
    }

    @Test
    void eachEffectWorksOnItsOwnCopy() {
        TestDTO original = dto();
        List<Object> seen = new ArrayList<>();
        BaseEntityFinalizer<TestDTO> finalizer = finalizer(
            Map.of(
                "onDeleting",
                List.of(
                    (stage, d, out) -> {
                        seen.add(d.getSpec().get("a"));
                        d.getSpec().put("a", 99);
                    },
                    (stage, d, out) -> seen.add(d.getSpec().get("a"))
                )
            )
        );

        finalizer.finalize(original);

        assertThat(seen).containsExactly(1, 1);
        assertThat(original.getSpec()).containsEntry("a", 1);
    }

    @Test
    void withoutRegistryNothingRuns() {
        BaseEntityFinalizer<TestDTO> finalizer = new BaseEntityFinalizer<TestDTO>() {};

        finalizer.finalize(dto());
    }
}
