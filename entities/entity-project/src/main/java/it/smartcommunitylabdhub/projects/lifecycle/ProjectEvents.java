/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.projects.lifecycle;

import it.smartcommunitylabdhub.commons.lifecycle.LifecycleEvents;
import it.smartcommunitylabdhub.projects.Project;

public enum ProjectEvents implements LifecycleEvents<Project> {
    CREATE,
    UPDATE,
    DELETE,
    READY,
    ERROR,
}
