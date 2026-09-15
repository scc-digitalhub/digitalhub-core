/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.compile;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Standard Apache TVM measurement runner used by MetaSchedule. */
public enum TvmTuningRunner {
    local,
    rpc;

    @JsonCreator
    public static TvmTuningRunner fromValue(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.toLowerCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown tuning_runner: " + value, e);
        }
    }
}