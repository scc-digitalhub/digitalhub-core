/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import it.smartcommunitylabdhub.runtime.tvm.specs.model.TvmFormat;
import org.junit.jupiter.api.Test;

class TvmBuildRunnerTest {

    @Test
    void explicitFormatWinsOverKindAndExtension() {
        assertEquals(
            TvmFormat.tflite,
            TvmBuildRunner.resolveFormat(TvmFormat.tflite, "store://p/model/onnx/m:1", "s3://b/m.onnx")
        );
    }

    @Test
    void typedModelKindDecidesTheFormatWithoutExtension() {
        assertEquals(TvmFormat.onnx, TvmBuildRunner.resolveFormat(null, "store://p/model/onnx/m:1", "s3://b/m/"));
        assertEquals(
            TvmFormat.tflite,
            TvmBuildRunner.resolveFormat(TvmFormat.auto, "store://p/model/tflite/m", "s3://b/m/")
        );
    }

    @Test
    void genericModelsFallBackToTheFileExtension() {
        assertEquals(
            TvmFormat.onnx,
            TvmBuildRunner.resolveFormat(null, "store://p/model/model/m:1", "s3://b/yolo.ONNX")
        );
        assertEquals(TvmFormat.tflite, TvmBuildRunner.resolveFormat(null, "https://h/x.tflite", "https://h/x.tflite"));
        assertThrows(IllegalArgumentException.class, () ->
            TvmBuildRunner.resolveFormat(null, "store://p/model/model/m:1", "s3://b/m/")
        );
    }

    @Test
    void folderSourcesUseTheDefaultFileName() {
        assertEquals("yolo.onnx", TvmBuildRunner.inputFileName("s3://b/models/yolo.onnx", TvmFormat.onnx));
        assertEquals("model.tflite", TvmBuildRunner.inputFileName("s3://b/models/", TvmFormat.tflite));
    }
}
