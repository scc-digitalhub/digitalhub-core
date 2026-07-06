/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.model;

// Target hardware for tvm+compile. Each constant expands to a full tvm.target.Target
// string. CPU/LLVM targets only: cuda was removed on purpose — the platform has no
// GPU serving path, so a cuda .so would compile "green" but be unloadable; re-add it
// together with a GPU-capable serve image if/when that path exists.
public enum TvmTargetArchitecture {
    llvm("llvm"),
    x86("{\"kind\":\"llvm\",\"mcpu\":\"x86-64-v2\"}"),
    arm64("{\"kind\":\"llvm\",\"mtriple\":\"aarch64-linux-gnu\"}");

    private final String target;

    TvmTargetArchitecture(String target) {
        this.target = target;
    }

    public String getValue() {
        return target;
    }
}
