/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.config;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

// Binds runtime.tvm.*: task images, injected pod scripts, and pod identity defaults.
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class TvmProperties {

    // Pod identity and storage defaults applied to the task pods.
    private Integer userId;
    private Integer groupId;
    private String homeDir;
    private String volumeSize;

    // Source format (onnx) -> builder image used by tvm+build.
    private Map<String, String> builders;

    // Image running compiler.py for tvm+compile (Relax IR -> model.so).
    private String compiler;

    // Base serving image for tvm+serve (defaults to the rust runtime); init container injects the .so Model.
    private String serve;

    // entrypoint.sh and the per-format builder scripts injected into build pods.
    private String entrypoint;
    private Map<String, String> builderScripts;
}
