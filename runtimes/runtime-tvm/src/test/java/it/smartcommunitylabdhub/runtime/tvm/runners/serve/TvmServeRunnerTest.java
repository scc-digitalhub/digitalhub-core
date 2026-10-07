/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners.serve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import it.smartcommunitylabdhub.runtime.tvm.specs.serve.TvmServeRuntime;
import it.smartcommunitylabdhub.runtime.tvm.specs.serve.TvmServeTaskSpec;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TvmServeRunnerTest {

    private static final Map<String, String> IMAGES = Map.of("go", "tvm-runtime-go:1", "rust", "tvm-runtime-rust:1");

    private static TvmServeTaskSpec task(TvmServeRuntime runtime, String image) {
        TvmServeTaskSpec spec = new TvmServeTaskSpec();
        spec.setServeRuntime(runtime);
        spec.setImage(image);
        return spec;
    }

    @Test
    void servesWithGoByDefault() {
        assertEquals("tvm-runtime-go:1", TvmServeRunner.serveImage(task(null, null), IMAGES));
    }

    @Test
    void servesWithTheImageOfTheChosenRuntime() {
        assertEquals("tvm-runtime-rust:1", TvmServeRunner.serveImage(task(TvmServeRuntime.rust, null), IMAGES));
        assertEquals("tvm-runtime-go:1", TvmServeRunner.serveImage(task(TvmServeRuntime.go, ""), IMAGES));
    }

    @Test
    void theTaskImageWinsOverTheRuntime() {
        assertEquals("custom:1", TvmServeRunner.serveImage(task(TvmServeRuntime.rust, "custom:1"), IMAGES));
    }

    @Test
    void refusesARuntimeWithoutAnImage() {
        assertThrows(
            IllegalArgumentException.class,
            () -> TvmServeRunner.serveImage(task(TvmServeRuntime.rust, null), Map.of("go", "tvm-runtime-go:1"))
        );
    }

    @Test
    void readsTheRuntimeFromTheSpecInAnyCase() {
        assertEquals(TvmServeRuntime.rust, TvmServeTaskSpec.with(Map.of("serve_runtime", "Rust")).getServeRuntime());
        assertThrows(IllegalArgumentException.class, () -> TvmServeTaskSpec.with(Map.of("serve_runtime", "python")));
    }

    @Test
    void splitsTheRequestedCoresAmongWorkers() {
        assertEquals(4, TvmServeRunner.threadsPerWorker(4, null));
        assertEquals(2, TvmServeRunner.threadsPerWorker(4, 2));
        assertEquals(1, TvmServeRunner.threadsPerWorker(2, 4));
    }

    @Test
    void leavesTheThreadCountToTvmWithoutACpuRequest() {
        assertNull(TvmServeRunner.threadsPerWorker(null, 2));
    }
}
