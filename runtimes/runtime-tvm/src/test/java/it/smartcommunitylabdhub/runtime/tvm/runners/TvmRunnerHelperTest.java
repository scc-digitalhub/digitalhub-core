/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
