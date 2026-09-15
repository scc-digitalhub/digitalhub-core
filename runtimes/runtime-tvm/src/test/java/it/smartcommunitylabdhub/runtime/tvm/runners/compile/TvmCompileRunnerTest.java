/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners.compile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmCompileTaskSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmTuningMode;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmTuningRunner;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TvmCompileRunnerTest {

        @Test
        void parsesStandardKubernetesCpuQuantities() {
                assertEquals(1, TvmCompileRunner.parseCpuCores("500m"));
                assertEquals(2, TvmCompileRunner.parseCpuCores("1500m"));
                assertEquals(2, TvmCompileRunner.parseCpuCores("2"));
                assertEquals(1, TvmCompileRunner.parseCpuCores("0.25"));
                assertThrows(IllegalArgumentException.class, () -> TvmCompileRunner.parseCpuCores("0"));
                assertThrows(IllegalArgumentException.class, () -> TvmCompileRunner.parseCpuCores("invalid"));
        }

        @Test
        void validatesTuneLifecycleAndResourceBounds() {
                TvmCompileTaskSpec spec = new TvmCompileTaskSpec();
                spec.setTuningMode(TvmTuningMode.tune);
                assertThrows(
                                IllegalArgumentException.class,
                                () -> TvmCompileRunner.validateTuning(spec, null, 2, 2));

                spec.setTuningTrials(32);
                spec.setTuningWorkers(3);
                assertThrows(
                                IllegalArgumentException.class,
                                () -> TvmCompileRunner.validateTuning(spec, null, 2, 2));

                spec.setTuningWorkers(2);
                assertDoesNotThrow(() -> TvmCompileRunner.validateTuning(spec, null, 2, 2));
        }

        @Test
        void requiresRpcForCrossCompiledTuningAndCompleteTrackerConfig() {
                TvmCompileTaskSpec spec = new TvmCompileTaskSpec();
                spec.setTuningMode(TvmTuningMode.tune);
                spec.setTuningTrials(32);
                assertThrows(
                                IllegalArgumentException.class,
                                () -> TvmCompileRunner.validateTuning(spec, "aarch64-linux-gnu-g++", 4, 2));

                spec.setTuningRunner(TvmTuningRunner.rpc);
                assertThrows(
                                IllegalArgumentException.class,
                                () -> TvmCompileRunner.validateTuning(spec, "aarch64-linux-gnu-g++", 4, 2));

                spec.setRpcTrackerHost("tracker");
                spec.setRpcTrackerPort(9190);
                spec.setRpcTrackerKey("pi5");
                assertDoesNotThrow(
                                () -> TvmCompileRunner.validateTuning(spec, "aarch64-linux-gnu-g++", 4, 2));
        }

        @Test
        void requiresDatabaseModelForApply() {
                TvmCompileTaskSpec spec = new TvmCompileTaskSpec();
                spec.setTuningMode(TvmTuningMode.apply);
                assertThrows(
                                IllegalArgumentException.class,
                                () -> TvmCompileRunner.validateTuning(spec, null, 2, 2));

                spec.setTuningModelPath("store://project/model/tuned");
                assertDoesNotThrow(() -> TvmCompileRunner.validateTuning(spec, null, 2, 2));
        }

        @Test
        void propagatesRunnerAllocationRepeat() {
                TvmCompileTaskSpec spec = new TvmCompileTaskSpec();
                spec.setTuningAllocRepeat(4);
                List<CoreEnv> envs = new ArrayList<>();

                TvmCompileRunner.addTuningEnvironment(envs, spec, null);

                assertEquals(List.of(new CoreEnv("TVM_TUNING_ALLOC_REPEAT", "4")), envs);
        }
}