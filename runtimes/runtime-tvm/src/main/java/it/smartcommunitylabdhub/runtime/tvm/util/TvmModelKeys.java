/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.util;

// "algorithm" tags stamped on the Models produced by the TVM tasks, so the
// artifacts can be told apart: tvm+build emits the Relax IR, tvm+compile the model.so.
public final class TvmModelKeys {

    public static final String ALGORITHM_SO = "tvm-compiled-so";

    private TvmModelKeys() {}
}
