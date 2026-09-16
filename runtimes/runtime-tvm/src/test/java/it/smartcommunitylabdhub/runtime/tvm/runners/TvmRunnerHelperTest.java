/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.runtime.tvm.specs.model.TvmTargetArchitecture;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TvmRunnerHelperTest {

    @Test
    void parsesStandardKubernetesCpuQuantities() {
        assertEquals(1, TvmRunnerHelper.parseCpuCores("500m"));
        assertEquals(2, TvmRunnerHelper.parseCpuCores("1500m"));
        assertEquals(2, TvmRunnerHelper.parseCpuCores("2"));
        assertEquals(1, TvmRunnerHelper.parseCpuCores("0.25"));
        assertThrows(IllegalArgumentException.class, () -> TvmRunnerHelper.parseCpuCores("0"));
        assertThrows(IllegalArgumentException.class, () -> TvmRunnerHelper.parseCpuCores("invalid"));
    }

    @Test
    void mapsEveryCompileTargetToItsNodeArchitecture() {
        assertNull(TvmRunnerHelper.targetArchitecture(TvmTargetArchitecture.cpu.getValue()));
        assertEquals("amd64", TvmRunnerHelper.targetArchitecture(TvmTargetArchitecture.x86.getValue()));
        assertEquals("amd64", TvmRunnerHelper.targetArchitecture(TvmTargetArchitecture.x86_v3.getValue()));
        assertEquals("arm64", TvmRunnerHelper.targetArchitecture(TvmTargetArchitecture.arm64.getValue()));
        assertEquals("arm64", TvmRunnerHelper.targetArchitecture(TvmTargetArchitecture.arm64_pi5.getValue()));
        assertEquals("arm", TvmRunnerHelper.targetArchitecture(TvmTargetArchitecture.armv7l.getValue()));
        assertEquals("arm64", TvmRunnerHelper.targetArchitecture("llvm -mtriple=aarch64-linux-gnu -mcpu=cortex-a76"));
        assertNull(TvmRunnerHelper.tripleArchitecture("riscv64-unknown-linux-gnu"));
    }

    @Test
    void readsTheModelArchitectureFromTheTripleWrittenByTheCompile() {
        Map<String, Serializable> spec = new HashMap<>();
        assertNull(TvmRunnerHelper.modelArchitecture(null));
        assertNull(TvmRunnerHelper.modelArchitecture(spec));

        // Models compiled before target_triple: only the target tells the architecture.
        spec.put("target", TvmTargetArchitecture.armv7l.getValue());
        assertEquals("arm", TvmRunnerHelper.modelArchitecture(spec));

        // A plain llvm target builds for the compile node, whose triple is in the manifest.
        spec.put("target", "llvm");
        spec.put("manifest", new HashMap<>(Map.of("target_triple", "aarch64-unknown-linux-gnu")));
        assertEquals("arm64", TvmRunnerHelper.modelArchitecture(spec));
    }

    @Test
    void readsTheModelKindFromStoreKeysOnly() {
        assertEquals("onnx", TvmRunnerHelper.modelKindOf("store://demo/model/onnx/yolo:1234"));
        assertEquals("model", TvmRunnerHelper.modelKindOf("store://demo/model/model/yolo"));
        assertNull(TvmRunnerHelper.modelKindOf("s3://bucket/models/yolo.onnx"));
        assertNull(TvmRunnerHelper.modelKindOf(null));
    }

    @Test
    void cleansFunctionNamesAndFileNames() {
        assertEquals("yolo", TvmRunnerHelper.cleanName("function/tvm/yolo:1234"));
        assertEquals("yolo", TvmRunnerHelper.cleanName("yolo"));
        assertEquals("model.onnx", TvmRunnerHelper.extractFileName("s3://bucket/a/model.onnx"));
        assertEquals("folder", TvmRunnerHelper.extractFileName("s3://bucket/a/folder/"));
        assertEquals("", TvmRunnerHelper.extractFileName(null));
    }

    @Test
    void mountsTheTaskScriptWithTheSharedModules() {
        String script = TvmRunnerHelper.loadClasspath(TvmRunnerHelper.SCRIPTS_CLASSPATH + "compile_model.py");
        List<ContextSource> sources = TvmRunnerHelper.createContextSources("#!/bin/sh", "compile_model.py", script);

        List<String> names = sources.stream().map(ContextSource::getName).toList();
        assertEquals(
            List.of("entrypoint.sh", "compile_model.py", "common.py", "publish.py", "tuning.py", "benchmark.py"),
            names
        );
        // Every mounted file exists on the classpath and is not empty.
        sources.forEach(source ->
            assertTrue(source.getBase64() != null && !source.getBase64().isEmpty(), source.getName())
        );
        assertEquals("build_onnx.py", TvmRunnerHelper.scriptName("classpath:/runtime-tvm/scripts/build_onnx.py"));
    }
}
