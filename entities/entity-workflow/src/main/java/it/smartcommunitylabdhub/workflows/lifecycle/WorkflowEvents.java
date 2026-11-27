/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.workflows.lifecycle;

import it.smartcommunitylabdhub.commons.lifecycle.LifecycleEvents;
import it.smartcommunitylabdhub.workflows.Workflow;

public enum WorkflowEvents implements LifecycleEvents<Workflow> {
    CREATE,
    UPDATE,
    DELETE,
    READY,
    ERROR,
}
