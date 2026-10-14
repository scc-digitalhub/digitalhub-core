/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

public enum TvmTargetArchitecture {
    // Generic LLVM code for the machine running the compile Job.
    auto("Compile node CPU - generic", "llvm"),
    amd64("Intel/AMD 64-bit - x86-64-v2", "{\"kind\":\"llvm\",\"mcpu\":\"x86-64-v2\"}"),
    // Requires AVX2 and FMA on the serving CPU.
    amd64_v3("Intel/AMD 64-bit - x86-64-v3 (AVX2)", "{\"kind\":\"llvm\",\"mcpu\":\"x86-64-v3\"}"),
    // Resolved by compile_model.py to the compiler host's CPU and triple.
    amd64_native("Intel/AMD 64-bit - compile node CPU (native)", "{\"kind\":\"llvm\",\"mcpu\":\"native\"}"),
    arm64("ARM 64-bit - generic (AArch64)", "{\"kind\":\"llvm\",\"mtriple\":\"aarch64-linux-gnu\"}"),
    arm64_pi5(
        "Raspberry Pi 5 - ARM 64-bit (Cortex-A76)",
        "{\"kind\":\"llvm\",\"mtriple\":\"aarch64-linux-gnu\",\"mcpu\":\"cortex-a76\",\"mattr\":[\"+neon\"],\"num-cores\":4}"
    ),
    // Keep the four-part triple and hard-float flags to preserve the ARM EABI calling convention.
    arm32(
        "ARM 32-bit - ARMv7 (hard-float, NEON)",
        "{\"kind\":\"llvm\",\"mtriple\":\"armv7l-unknown-linux-gnueabihf\",\"mfloat-abi\":\"hard\",\"mattr\":[\"+neon\"]}"
    );

    private final String label;
    private final String target;

    TvmTargetArchitecture(String label, String target) {
        this.label = label;
        this.target = target;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return target;
    }

    @JsonCreator
    public static TvmTargetArchitecture fromValue(String value) {
        if (value == null) {
            return null;
        }
        String name = value.toLowerCase(Locale.ROOT);
        try {
            return switch (name) {
                case "cpu", "llvm" -> auto;
                case "x86" -> amd64;
                case "x86_v3" -> amd64_v3;
                case "x86_native" -> amd64_native;
                case "armv7l" -> arm32;
                default -> valueOf(name);
            };
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown target_architecture: " + value);
        }
    }
}
