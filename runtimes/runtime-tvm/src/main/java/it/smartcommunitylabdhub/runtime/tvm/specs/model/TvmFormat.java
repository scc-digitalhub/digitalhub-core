/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.model;

import java.util.Locale;

public enum TvmFormat {
    auto,
    onnx,
    tflite;

    // Detect the source format from the file extension; each constant is named after
    // its own extension, so a new format only needs a new constant here.
    public static TvmFormat fromPath(String path) {
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        for (TvmFormat format : values()) {
            if (format != auto && lower.endsWith("." + format.name())) {
                return format;
            }
        }
        throw new IllegalArgumentException(
                "cannot detect the TVM source format of '" + path +
                        "': set spec.format explicitly (onnx, tflite) when the source has no " +
                        "recognizable extension (e.g. a store:// or folder path)");
    }
}
