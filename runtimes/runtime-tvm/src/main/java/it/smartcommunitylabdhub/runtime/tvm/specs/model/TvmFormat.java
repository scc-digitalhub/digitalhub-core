/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.model;

import java.util.Locale;

// Source formats accepted by tvm+build. Each constant is named after its file extension
// and after the Model kind that carries it, so a new format only needs a new constant.
public enum TvmFormat {
    auto,
    onnx,
    tflite;

    // Format carried by a Model kind ("onnx", "tflite"); null for kinds that say nothing
    // about the format, such as the generic "model".
    public static TvmFormat fromModelKind(String kind) {
        for (TvmFormat format : values()) {
            if (format != auto && format.name().equalsIgnoreCase(kind)) {
                return format;
            }
        }
        return null;
    }

    // Format detected from the file extension of a path.
    public static TvmFormat fromPath(String path) {
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        for (TvmFormat format : values()) {
            if (format != auto && lower.endsWith("." + format.name())) {
                return format;
            }
        }
        throw new IllegalArgumentException(
            "cannot detect the TVM source format of '" +
                path +
                "': upload the model with kind onnx or tflite, or set spec.format explicitly, " +
                "when the source has no recognizable extension (e.g. a folder path)"
        );
    }
}
