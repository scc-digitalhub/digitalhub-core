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

package it.smartcommunitylabdhub.runs.service;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.core.services.BaseEntityServiceImpl;
import it.smartcommunitylabdhub.projects.Project;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runs.lifecycle.RunState;
import it.smartcommunitylabdhub.runs.persistence.RunEntity;
import it.smartcommunitylabdhub.runs.specs.RunBaseStatus;
import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Slf4j
public class RunEntityServiceImpl extends BaseEntityServiceImpl<Run, RunEntity> {

    //runs: no caller effect, the run lifecycle manager concludes the delete itself once the
    //runnable (child of the run) is gone, for every caller of perform(DELETE)
    @Override
    protected Run doDelete(@NotNull String id, @NotNull Run e, @Nullable Boolean cascade) throws StoreException {
        StatusFieldAccessor status = StatusFieldAccessor.with(e.getStatus());
        if (getLifecycleManager() != null && !RunState.DELETED.name().equals(status.getState())) {
            return getLifecycleManager().perform(e, "DELETE");
        }

        return super.doDelete(id, e, cascade);
    }

    private EntityRepository<Project> projectRepository;

    @Autowired
    public void setProjectRepository(EntityRepository<Project> projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    public Run create(@NotNull Run dto)
        throws IllegalArgumentException, BindException, DuplicatedEntityException, StoreException {
        String projectId = dto.getProject();
        if (!StringUtils.hasText(projectId) || projectRepository.find(projectId) == null) {
            throw new IllegalArgumentException("invalid or missing project");
        }

        RunSpecAccessor runSpecAccessor = RunSpecAccessor.with(dto.getSpec());
        //task is required, either as full reference or as generic identifier
        if (!StringUtils.hasText(runSpecAccessor.getTask())) {
            throw new IllegalArgumentException("spec: missing task");
        }
        //if full referenced project, it must match the one in the DTO
        if (StringUtils.hasText(runSpecAccessor.getProject()) && !projectId.equals(runSpecAccessor.getProject())) {
            throw new IllegalArgumentException("project mismatch");
        }

        //generate random name if missing OR if matching provided id
        if (
            !StringUtils.hasText(dto.getName()) ||
            (StringUtils.hasText(dto.getId()) && dto.getName().equals(dto.getId()))
        ) {
            String name = nameGenerator.generateKey();
            dto.setName(name);
        }

        return super.create(dto);
    }

    @Override
    public Run update(@NotNull String id, @NotNull Run dto, boolean forceUpdate, boolean merge)
        throws NoSuchEntityException, StoreException {
        Run current = super.get(id);

        //additional protection: spec is modifiable ONLY when run is in CREATED state then is sealed
        StatusFieldAccessor status = StatusFieldAccessor.with(current.getStatus());
        if (!RunState.CREATED.name().equals(status.getState())) {
            dto.setSpec(current.getSpec());
        }

        RunSpecAccessor specAccessor = RunSpecAccessor.with(current.getSpec());
        if (!specAccessor.isLocalExecution()) {
            //for MANAGED runs we explicitly avoid externally provided status updates
            RunBaseStatus baseStatus = RunBaseStatus.with(current.getSpec());
            dto.setStatus(MapUtils.mergeMultipleMaps(dto.getStatus(), baseStatus.toMap()));
        }

        return super.update(id, dto, forceUpdate, merge);
    }
}
