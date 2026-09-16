/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners.compile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmCompileTaskSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmTuningMode;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmTuningRunner;
import it.smartcommunitylabdhub.runtime.tvm.specs.model.TvmTargetArchitecture;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TvmCompileRunnerTest {

    @Test
    void validatesTuneLifecycleAndResourceBounds() {
        TvmCompileTaskSpec spec = new TvmCompileTaskSpec();
        spec.setTuningMode(TvmTuningMode.tune);
        assertThrows(IllegalArgumentException.class, () -> TvmCompileRunner.validateTuning(spec, null, 2, 2));

        spec.setTuningTrials(32);
        spec.setTuningWorkers(3);
        assertThrows(IllegalArgumentException.class, () -> TvmCompileRunner.validateTuning(spec, null, 2, 2));

        spec.setTuningWorkers(2);
        assertDoesNotThrow(() -> TvmCompileRunner.validateTuning(spec, null, 2, 2));
    }

    @Test
    void requiresRpcForCrossCompiledTuningAndCompleteTrackerConfig() {
        TvmCompileTaskSpec spec = new TvmCompileTaskSpec();
        spec.setTuningMode(TvmTuningMode.tune);
        spec.setTuningTrials(32);
        assertThrows(IllegalArgumentException.class, () ->
            TvmCompileRunner.validateTuning(spec, "aarch64-linux-gnu-g++", 4, 2)
        );

        spec.setTuningRunner(TvmTuningRunner.rpc);
        assertThrows(IllegalArgumentException.class, () ->
            TvmCompileRunner.validateTuning(spec, "aarch64-linux-gnu-g++", 4, 2)
        );

        spec.setRpcTrackerHost("tracker");
        spec.setRpcTrackerPort(9190);
        spec.setRpcTrackerKey("pi5");
        assertDoesNotThrow(() -> TvmCompileRunner.validateTuning(spec, "aarch64-linux-gnu-g++", 4, 2));
    }

    @Test
    void requiresDatabaseModelForApply() {
        TvmCompileTaskSpec spec = new TvmCompileTaskSpec();
        spec.setTuningMode(TvmTuningMode.apply);
        assertThrows(IllegalArgumentException.class, () -> TvmCompileRunner.validateTuning(spec, null, 2, 2));

        spec.setTuningModelPath("store://project/model/tuned");
        assertDoesNotThrow(() -> TvmCompileRunner.validateTuning(spec, null, 2, 2));
    }

    @Test
    void exportsOnlyTheTuningSettingsThatAreSet() {
        TvmCompileTaskSpec spec = new TvmCompileTaskSpec();
        spec.setTuningAllocRepeat(4);
        List<CoreEnv> envs = new ArrayList<>();

        TvmCompileRunner.addTuningEnvironment(envs, spec, null);

        assertEquals(List.of(new CoreEnv("TVM_TUNING_ALLOC_REPEAT", "4")), envs);
    }

    @Test
    void exportsTuningModeTrialsPerIterationAndDefaultWorkers() {
        TvmCompileTaskSpec spec = new TvmCompileTaskSpec();
        spec.setTuningMode(TvmTuningMode.tune);
        spec.setTuningTrialsPerIter(32);
        spec.setTuningOps(List.of("conv2d", "dense"));
        List<CoreEnv> envs = new ArrayList<>();

        TvmCompileRunner.addTuningEnvironment(envs, spec, 4);

        assertEquals(
            List.of(
                new CoreEnv("TVM_TUNING_MODE", "tune"),
                new CoreEnv("TVM_TUNING_TRIALS_PER_ITER", "32"),
                new CoreEnv("TVM_TUNING_OPS", "conv2d,dense"),
                new CoreEnv("TVM_TUNING_WORKERS", "4")
            ),
            envs
        );
    }

    @Test
    void picksTheCrossCompilerOfArmTargetsOnly() {
        assertEquals("aarch64-linux-gnu-g++", TvmCompileRunner.defaultCrossCc(TvmTargetArchitecture.arm64));
        assertEquals("aarch64-linux-gnu-g++", TvmCompileRunner.defaultCrossCc(TvmTargetArchitecture.arm64_pi5));
        assertEquals("arm-linux-gnueabihf-g++", TvmCompileRunner.defaultCrossCc(TvmTargetArchitecture.armv7l));
        assertNull(TvmCompileRunner.defaultCrossCc(TvmTargetArchitecture.x86_v3));
        assertNull(TvmCompileRunner.defaultCrossCc(TvmTargetArchitecture.cpu));
    }
}
