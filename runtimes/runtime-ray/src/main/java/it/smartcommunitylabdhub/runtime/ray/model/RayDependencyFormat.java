/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.ray.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Supported dependency formats for the Ray runtime environment.
 *
 * <p>Mirrors the {@code pip}/{@code conda}/{@code uv} options accepted by
 * Ray's runtime environment specification. The string form (lower-case) is
 * what gets serialized into JSON and what the Ray operator expects.</p>
 */
public enum RayDependencyFormat {
    pip,
    conda,
    uv;

    @JsonValue
    public String value() {
        return name();
    }

    @JsonCreator
    public static RayDependencyFormat from(String value) {
        if (value == null) {
            return null;
        }
        return RayDependencyFormat.valueOf(value.toLowerCase());
    }
}
