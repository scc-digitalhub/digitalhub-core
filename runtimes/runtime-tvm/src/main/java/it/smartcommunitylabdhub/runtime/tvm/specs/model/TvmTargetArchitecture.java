/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.model;

import com.fasterxml.jackson.annotation.JsonCreator;

// Target hardware for tvm+compile. Each constant expands to a full tvm.target.Target
// string, always via the LLVM backend. cuda was removed on purpose — the platform has
// no GPU serving path, so a cuda .so would compile "green" but be unloadable; re-add it
// together with a GPU-capable serve image if/when that path exists.
public enum TvmTargetArchitecture {
    // Generic host CPU, portable baseline. Renamed from "llvm" (that was the codegen
    // backend, not an architecture); "llvm" is still accepted as a legacy input alias.
    cpu("llvm"),
    x86("{\"kind\":\"llvm\",\"mcpu\":\"x86-64-v2\"}"),
    arm64("{\"kind\":\"llvm\",\"mtriple\":\"aarch64-linux-gnu\"}");

    private final String target;

    TvmTargetArchitecture(String target) {
        this.target = target;
    }

    public String getValue() {
        return target;
    }

    // Accept the current names plus the legacy "llvm" alias for cpu, so specs written
    // before the rename still deserialize. Serialization stays the constant name
    // (cpu/x86/arm64) via Jackson's default enum handling.
    @JsonCreator
    public static TvmTargetArchitecture fromValue(String value) {
        if (value == null) {
            return null;
        }
        switch (value.toLowerCase()) {
            case "cpu":
            case "llvm": // legacy alias
                return cpu;
            case "x86":
                return x86;
            case "arm64":
                return arm64;
            default:
                throw new IllegalArgumentException("unknown target_architecture: " + value);
        }
    }
}
