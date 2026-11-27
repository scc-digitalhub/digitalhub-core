/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.workflows.lifecycle;

import it.smartcommunitylabdhub.commons.lifecycle.LifecycleState;
import it.smartcommunitylabdhub.workflows.Workflow;

public enum WorkflowState implements LifecycleState<Workflow> {
    CREATED,
    READY,
    ERROR,
    DELETING,
    DELETED,
}
