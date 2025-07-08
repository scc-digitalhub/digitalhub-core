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

package it.smartcommunitylabdhub.core.artifacts.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArtifactRepository
    extends JpaRepository<ArtifactEntity, String>, JpaSpecificationExecutor<ArtifactEntity> {
    List<ArtifactEntity> findByProject(String project);

    Page<ArtifactEntity> findAll(Pageable pageable);

    @Query(
        "SELECT a FROM ArtifactEntity a WHERE (a.name, a.project, a.created) IN " +
        "(SELECT a2.name, a2.project, MAX(a2.created) FROM ArtifactEntity a2 GROUP BY a2.name, a2.project) " +
        "ORDER BY a.created DESC"
    )
    List<ArtifactEntity> findAllLatestArtifacts();

    ////////////////////////////
    // CONTEXT SPECIFIC QUERY //
    ////////////////////////////

    @Query(
        "SELECT a FROM ArtifactEntity a WHERE a.project = :project AND (a.name, a.project, a.created) IN " +
        "(SELECT a2.name, a2.project, MAX(a2.created) FROM ArtifactEntity a2 WHERE a2.project = :project GROUP BY a2.name, a2.project) " +
        "ORDER BY a.created DESC"
    )
    List<ArtifactEntity> findAllLatestArtifactsByProject(@Param("project") String project);

    Optional<ArtifactEntity> findByProjectAndNameAndId(
        @Param("project") String project,
        @Param("name") String name,
        @Param("id") String id
    );

    @Query(
        "SELECT a FROM ArtifactEntity a WHERE a.project = :project AND a.name = :name " +
        "AND a.created = (SELECT MAX(a2.created) FROM ArtifactEntity a2 WHERE a2.project = :project AND a2.name = :name)"
    )
    Optional<ArtifactEntity> findLatestArtifactByProjectAndName(
        @Param("project") String project,
        @Param("name") String name
    );

    boolean existsByProjectAndNameAndId(String project, String name, String id);

    @Modifying
    @Query("DELETE FROM ArtifactEntity a WHERE a.project = :project AND a.name = :name AND a.id = :id")
    void deleteByProjectAndNameAndId(
        @Param("project") String project,
        @Param("name") String name,
        @Param("id") String id
    );

    boolean existsByProjectAndName(String project, String name);

    @Modifying
    @Query("DELETE FROM ArtifactEntity a WHERE a.project = :project AND a.name = :name ")
    void deleteByProjectAndName(@Param("project") String project, @Param("name") String name);

    @Modifying
    @Query("DELETE FROM ArtifactEntity a WHERE a.project = :project ")
    void deleteByProjectName(@Param("project") String project);
}
