/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum TvmTargetArchitecture {
    cpu("llvm"),
    x86("{\"kind\":\"llvm\",\"mcpu\":\"x86-64-v2\"}"),
    // compiler.py resolves native to LLVM's concrete CPU and system triple. The
    // core count comes from task.target_num_cores (or the compiler CPU affinity).
    x86_native("{\"kind\":\"llvm\",\"mcpu\":\"native\"}"),
    arm64("{\"kind\":\"llvm\",\"mtriple\":\"aarch64-linux-gnu\"}"),
    arm64_pi5("{\"kind\":\"llvm\",\"mtriple\":\"aarch64-linux-gnu\",\"mcpu\":\"cortex-a76\",\"mattr\":[\"+neon\"],\"num-cores\":4}"),
    // 32-bit ARM hard-float (Raspberry Pi armhf). mfloat-abi=hard + a VFP unit are
    // required, else LLVM emits soft-float objects that won't link against the
    // hard-float cross g++ ("uses VFP register arguments" error).
    armv7l("{\"kind\":\"llvm\",\"mtriple\":\"armv7l-linux-gnueabihf\",\"mfloat-abi\":\"hard\",\"mattr\":[\"+neon\"]}");

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
