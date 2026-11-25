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

package it.smartcommunitylabdhub.workflows.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.search.service.IndexableEntityService;
import it.smartcommunitylabdhub.workflows.Workflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflows")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Validated
@Slf4j
@Tag(name = "Workflow base API", description = "Endpoints related to workflows management out of the Context")
public class WorkflowController {

    @Autowired
    IndexableEntityService<Workflow> indexService;

    /*
     * Search apis
     */
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Reindex all workflow", description = "Reindex workflows")
    @PostMapping(value = "/search/reindex", produces = "application/json; charset=UTF-8")
    public void reindexWorkflows() {
        //via async
        indexService.reindexAll();
    }
}
