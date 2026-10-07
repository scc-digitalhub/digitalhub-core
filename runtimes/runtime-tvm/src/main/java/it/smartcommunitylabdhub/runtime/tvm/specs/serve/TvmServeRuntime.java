/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.serve;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum TvmServeRuntime {
    // Native Go runtime from digitalhub-serverless
    go,
    // Rust server from digitalhub-tvm-rust.
    rust;

    @JsonCreator
    public static TvmServeRuntime fromValue(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.toLowerCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown serve_runtime: " + value, e);
        }
    }
}
