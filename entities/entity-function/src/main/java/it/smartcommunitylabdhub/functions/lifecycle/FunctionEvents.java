/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.functions.lifecycle;

import it.smartcommunitylabdhub.commons.lifecycle.LifecycleEvents;
import it.smartcommunitylabdhub.functions.Function;

public enum FunctionEvents implements LifecycleEvents<Function> {
    CREATE,
    UPDATE,
    DELETE,
    READY,
    ERROR,
}
