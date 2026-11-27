/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.projects.lifecycle;

import it.smartcommunitylabdhub.commons.lifecycle.LifecycleState;
import it.smartcommunitylabdhub.projects.Project;

public enum ProjectState implements LifecycleState<Project> {
    CREATED,
    READY,
    ERROR,
    DELETING,
    DELETED,
}
