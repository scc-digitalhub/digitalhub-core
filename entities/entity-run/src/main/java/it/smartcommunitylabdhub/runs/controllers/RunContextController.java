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

package it.smartcommunitylabdhub.runs.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.core.controllers.BaseEntityController;
import it.smartcommunitylabdhub.lifecycle.LifecycleManager;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runs.lifecycle.RunEvent;
import it.smartcommunitylabdhub.runs.lifecycle.RunState;
import it.smartcommunitylabdhub.runs.specs.RunBaseSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/-/{project}/runs")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "Run context API", description = "Endpoints related to runs management for project")
public class RunContextController extends BaseEntityController<Run> {

    @Autowired
    LifecycleManager<Run> lifecycleManager;

    @Override
    @Operation(summary = "Create a run in a project context")
    public Run create(String project, Run dto)
        throws DuplicatedEntityException, IllegalArgumentException, BindException, StoreException {
        //enforce project match
        dto.setProject(project);

        //create as new, will check for duplicated
        Run run = super.create(project, dto);

        if (run != null && RunState.CREATED.name().equals(StatusFieldAccessor.with(run.getStatus()).getState())) {
            //if !local then also build+run
            RunBaseSpec runBaseSpec = new RunBaseSpec();
            runBaseSpec.configure(run.getSpec());

            if (Boolean.FALSE.equals(runBaseSpec.getLocalExecution())) {
                run = lifecycleManager.perform(run, RunEvent.BUILD.name());
                run = lifecycleManager.perform(run, RunEvent.RUN.name());
            }
        }

        return run;
    }
}
