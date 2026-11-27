/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.files.service;

import jakarta.validation.constraints.NotNull;

/**
 * Resolve the base path for the files of a project.
 *
 * Every managed file of a project lives under its base path: <base>/<entity type>/<name>/<id>/.
 * The default resolves to the default store + "/<project>/"; a project aware implementation
 * (marked @Primary) takes the project configuration into account.
 */
@FunctionalInterface
public interface ProjectPathResolver {
    /**
     * @param project the project id
     * @return the base path of the project files, ending with "/"
     */
    @NotNull
    String resolve(@NotNull String project);
}
