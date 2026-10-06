/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners.serve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TvmServeRunnerTest {

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
