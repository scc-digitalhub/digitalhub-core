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

    public static TvmFormat fromModelKind(String kind) {
        for (TvmFormat format : values()) {
            if (format != auto && format.name().equalsIgnoreCase(kind)) {
                return format;
            }
        }
        return null;
    }

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
