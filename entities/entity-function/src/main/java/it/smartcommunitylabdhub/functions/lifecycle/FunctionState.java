/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.functions.lifecycle;

import it.smartcommunitylabdhub.commons.lifecycle.LifecycleState;
import it.smartcommunitylabdhub.functions.Function;

public enum FunctionState implements LifecycleState<Function> {
    CREATED,
    READY,
    ERROR,
    DELETING,
    DELETED,
}
