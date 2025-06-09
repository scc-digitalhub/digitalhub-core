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

package it.smartcommunitylabdhub.core.runs.service;

import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.services.RunService;
import it.smartcommunitylabdhub.core.runs.persistence.RunEntity;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/*
 * Searchable service for managing run
 */
public interface SearchableRunService extends RunService {
    /**
     * List all runs, with optional filters
     * @param pageable
     * @param filter
     * @return
     */
    Page<Run> searchRuns(Pageable pageable, @Nullable SearchFilter<RunEntity> filter) throws SystemException;

    /**
     * List the runs for a given project, with optional filters
     * @param project
     * @param pageable
     * @param filter
     * @return
     */
    Page<Run> searchRunsByProject(@NotNull String project, Pageable pageable, @Nullable SearchFilter<RunEntity> filter)
        throws SystemException;
}
