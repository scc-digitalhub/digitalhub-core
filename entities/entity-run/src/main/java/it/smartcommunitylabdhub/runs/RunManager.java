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

package it.smartcommunitylabdhub.runs;

import it.smartcommunitylabdhub.commons.services.EntityAwareService;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.commons.services.SchemaService;
import it.smartcommunitylabdhub.commons.services.SpecRegistry;
import it.smartcommunitylabdhub.core.events.EntityOperationsListener;
import it.smartcommunitylabdhub.core.services.EntityFinalizer;
import it.smartcommunitylabdhub.lifecycle.LifecycleManager;
import it.smartcommunitylabdhub.metrics.service.MetricsService;
import it.smartcommunitylabdhub.relationships.RelationshipsAwareEntityService;
import lombok.experimental.Delegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/*
 * Exposed manager for run-related operations via services
 */
@Component
public class RunManager {

    @Delegate(excludes = { EntityAwareService.class })
    private EntityService<Run> runEntityService;

    @Delegate(excludes = { EntityAwareService.class })
    private LifecycleManager<Run> runLifecycleManager;

    @Delegate(excludes = { EntityAwareService.class })
    private RelationshipsAwareEntityService<Run> runRelationshipsAwareEntityService;

    @Delegate(excludes = { EntityAwareService.class })
    private MetricsService<Run> runMetricsService;

    @Delegate(excludes = { EntityAwareService.class })
    private EntityFinalizer<Run> runEntityFinalizer;

    @Delegate(excludes = { EntityAwareService.class })
    private EntityOperationsListener<Run> runOperationsListener;

    @Delegate(excludes = { EntityAwareService.class })
    private SpecRegistry<Run> runSpecRegistry;

    @Delegate(excludes = { EntityAwareService.class })
    private SchemaService<Run> runSchemaService;

    @Autowired
    public void setRunEntityService(EntityService<Run> runEntityService) {
        this.runEntityService = runEntityService;
    }

    @Autowired
    public void setRunLifecycleManager(LifecycleManager<Run> runLifecycleManager) {
        this.runLifecycleManager = runLifecycleManager;
    }

    @Autowired
    public void setRunRelationshipsAwareEntityService(
        RelationshipsAwareEntityService<Run> runRelationshipsAwareEntityService
    ) {
        this.runRelationshipsAwareEntityService = runRelationshipsAwareEntityService;
    }

    @Autowired
    public void setRunEntityFinalizer(EntityFinalizer<Run> runEntityFinalizer) {
        this.runEntityFinalizer = runEntityFinalizer;
    }

    @Autowired
    public void setRunOperationsListener(EntityOperationsListener<Run> runOperationsListener) {
        this.runOperationsListener = runOperationsListener;
    }

    @Autowired
    public void setRunSpecRegistry(SpecRegistry<Run> runSpecRegistry) {
        this.runSpecRegistry = runSpecRegistry;
    }

    @Autowired
    public void setRunMetricsService(MetricsService<Run> runMetricsService) {
        this.runMetricsService = runMetricsService;
    }

    @Autowired
    public void setRunSchemaService(SchemaService<Run> runSchemaService) {
        this.runSchemaService = runSchemaService;
    }
}
