/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum TvmTargetArchitecture {
    // Generic LLVM code for the machine running the compile Job.
    cpu("llvm"),
    x86("{\"kind\":\"llvm\",\"mcpu\":\"x86-64-v2\"}"),
    // Requires AVX2 and FMA on the serving CPU.
    x86_v3("{\"kind\":\"llvm\",\"mcpu\":\"x86-64-v3\"}"),
    // Resolved by compile_model.py to the compiler host's CPU and triple.
    x86_native("{\"kind\":\"llvm\",\"mcpu\":\"native\"}"),
    arm64("{\"kind\":\"llvm\",\"mtriple\":\"aarch64-linux-gnu\"}"),
    arm64_pi5(
        "{\"kind\":\"llvm\",\"mtriple\":\"aarch64-linux-gnu\",\"mcpu\":\"cortex-a76\",\"mattr\":[\"+neon\"],\"num-cores\":4}"
    ),
    // Keep the four-part triple and hard-float flags to preserve the ARM EABI calling convention.
    armv7l(
        "{\"kind\":\"llvm\",\"mtriple\":\"armv7l-unknown-linux-gnueabihf\",\"mfloat-abi\":\"hard\",\"mattr\":[\"+neon\"]}"
    );

    private final String target;

    TvmTargetArchitecture(String target) {
        this.target = target;
    }

    public String getValue() {
        return target;
    }

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
            case "x86_v3":
                return x86_v3;
            case "x86_native":
                return x86_native;
            case "arm64":
                return arm64;
            case "arm64_pi5":
                return arm64_pi5;
            case "armv7l":
                return armv7l;
            default:
                throw new IllegalArgumentException("unknown target_architecture: " + value);
        }
    }
}
