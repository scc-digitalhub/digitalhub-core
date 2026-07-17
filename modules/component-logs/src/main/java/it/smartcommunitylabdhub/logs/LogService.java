/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package it.smartcommunitylabdhub.logs;

import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/*
 * Service for accessing logs
 */
public interface LogService {
    /**
     * List all versions of every log for a project
     * @param project
     * @return
     */
    List<Log> listLogsByProject(@NotNull String project) throws SystemException;

    /**
     * List all versions of every log for a project
     * @param project
     * @param pageable
     * @return
     */
    Page<Log> listLogsByProject(@NotNull String project, @NonNull Pageable pageable) throws SystemException;

    /**
     * List all logs, with optional filters
     * @param pageable
     * @param filter
     * @return
     */
    Page<Log> searchLogs(@NonNull Pageable pageable, @Nullable SearchFilter<Log> filter) throws SystemException;

    /**
     * List the latest version of every log, with optional filters
     * @param project
     * @param pageable
     * @param filter
     * @return
     */
    Page<Log> searchLogsByProject(
        @NotNull String project,
        @NonNull Pageable pageable,
        @Nullable SearchFilter<Log> filter
    ) throws SystemException;

    /**
     * List all logs for a given run
     * @param run
     * @return
     */
    List<Log> getLogsByRunId(@NotNull String runId) throws SystemException;
}
