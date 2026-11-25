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

package it.smartcommunitylabdhub.models;

import it.smartcommunitylabdhub.commons.services.EntityAwareService;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.commons.services.SchemaService;
import it.smartcommunitylabdhub.commons.services.SpecRegistry;
import it.smartcommunitylabdhub.core.events.EntityOperationsListener;
import it.smartcommunitylabdhub.core.services.EntityFinalizer;
import it.smartcommunitylabdhub.core.services.VersionableEntityService;
import it.smartcommunitylabdhub.files.service.EntityFilesService;
import it.smartcommunitylabdhub.lifecycle.LifecycleManager;
import it.smartcommunitylabdhub.metrics.service.MetricsService;
import it.smartcommunitylabdhub.relationships.RelationshipsAwareEntityService;
import it.smartcommunitylabdhub.search.service.IndexableEntityService;
import lombok.experimental.Delegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/*
 * Exposed manager for model-related operations via services
 */
@Component
public class ModelManager {

    @Delegate(excludes = { EntityAwareService.class })
    private EntityService<Model> modelEntityService;

    @Delegate(excludes = { EntityAwareService.class })
    private VersionableEntityService<Model> modelVersionableEntityService;

    @Delegate(excludes = { EntityAwareService.class })
    private EntityFilesService<Model> modelFilesService;

    @Delegate(excludes = { EntityAwareService.class })
    private IndexableEntityService<Model> modelIndexableEntityService;

    @Delegate(excludes = { EntityAwareService.class })
    private MetricsService<Model> modelMetricsService;

    @Delegate(excludes = { EntityAwareService.class })
    private LifecycleManager<Model> modelLifecycleManager;

    @Delegate(excludes = { EntityAwareService.class })
    private RelationshipsAwareEntityService<Model> modelRelationshipsAwareEntityService;

    @Delegate(excludes = { EntityAwareService.class })
    private EntityFinalizer<Model> modelEntityFinalizer;

    @Delegate(excludes = { EntityAwareService.class })
    private EntityOperationsListener<Model> modelOperationsListener;

    @Delegate(excludes = { EntityAwareService.class })
    private SpecRegistry<Model> modelSpecRegistry;

    @Delegate(excludes = { EntityAwareService.class })
    private SchemaService<Model> modelSchemaService;

    @Autowired
    public void setModelEntityService(EntityService<Model> modelEntityService) {
        this.modelEntityService = modelEntityService;
    }

    @Autowired
    public void setModelVersionableEntityService(VersionableEntityService<Model> modelVersionableEntityService) {
        this.modelVersionableEntityService = modelVersionableEntityService;
    }

    @Autowired
    public void setModelFilesService(EntityFilesService<Model> modelFilesService) {
        this.modelFilesService = modelFilesService;
    }

    @Autowired
    public void setModelIndexableEntityService(IndexableEntityService<Model> modelIndexableEntityService) {
        this.modelIndexableEntityService = modelIndexableEntityService;
    }

    @Autowired
    public void setModelLifecycleManager(LifecycleManager<Model> modelLifecycleManager) {
        this.modelLifecycleManager = modelLifecycleManager;
    }

    @Autowired
    public void setModelRelationshipsAwareEntityService(
        RelationshipsAwareEntityService<Model> modelRelationshipsAwareEntityService
    ) {
        this.modelRelationshipsAwareEntityService = modelRelationshipsAwareEntityService;
    }

    @Autowired
    public void setModelEntityFinalizer(EntityFinalizer<Model> modelEntityFinalizer) {
        this.modelEntityFinalizer = modelEntityFinalizer;
    }

    @Autowired
    public void setModelOperationsListener(EntityOperationsListener<Model> modelOperationsListener) {
        this.modelOperationsListener = modelOperationsListener;
    }

    @Autowired
    public void setModelSpecRegistry(SpecRegistry<Model> modelSpecRegistry) {
        this.modelSpecRegistry = modelSpecRegistry;
    }

    @Autowired
    public void setModelSchemaService(SchemaService<Model> modelSchemaService) {
        this.modelSchemaService = modelSchemaService;
    }

    @Autowired
    public void setModelMetricsService(MetricsService<Model> modelMetricsService) {
        this.modelMetricsService = modelMetricsService;
    }
}
