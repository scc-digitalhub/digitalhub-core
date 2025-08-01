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

package it.smartcommunitylabdhub.core.runs.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RunRepository extends JpaRepository<RunEntity, String>, JpaSpecificationExecutor<RunEntity> {
    List<RunEntity> findByProject(String uuid);

    List<RunEntity> findByTask(String task);

    @Modifying
    @Query("DELETE FROM RunEntity r WHERE r.project = :project ")
    void deleteByProjectName(@Param("project") String project);

    @Modifying
    @Query("DELETE FROM RunEntity r WHERE r.task = :task ")
    void deleteByTask(String task);

    ////////////////////////////
    // CONTEXT SPECIFIC QUERY //
    ////////////////////////////

    Optional<RunEntity> findByProjectAndId(@Param("project") String project, @Param("id") String id);

    boolean existsByProjectAndId(String project, String id);

    @Modifying
    @Query("DELETE FROM RunEntity a WHERE a.project = :project AND a.id = :id")
    void deleteByProjectAndId(@Param("project") String project, @Param("id") String id);
}
