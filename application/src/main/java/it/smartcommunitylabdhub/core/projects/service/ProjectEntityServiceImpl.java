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

package it.smartcommunitylabdhub.core.projects.service;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.core.projects.persistence.ProjectEntity;
import it.smartcommunitylabdhub.core.services.BaseEntityServiceImpl;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
@Transactional
@Slf4j
public class ProjectEntityServiceImpl extends BaseEntityServiceImpl<Project, ProjectEntity> {

    @Override
    public void deleteByProject(@NotNull String project, @Nullable Boolean cascade) throws StoreException {
        //nothing to do
    }

    @Override
    public List<Project> listByProject(@NotNull String project) throws StoreException {
        return Collections.emptyList();
    }

    @Override
    public Page<Project> listByProject(@NotNull String project, Pageable pageable) throws StoreException {
        return Page.empty(pageable);
    }

    @Override
    public List<Project> searchByProject(@NotNull String project, SearchFilter<Project> filter) throws StoreException {
        return Collections.emptyList();
    }

    @Override
    public Page<Project> searchByProject(@NotNull String project, SearchFilter<Project> filter, Pageable pageable)
        throws StoreException {
        return Page.empty(pageable);
    }
}
