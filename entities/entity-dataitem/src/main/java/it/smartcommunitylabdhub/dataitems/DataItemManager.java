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

package it.smartcommunitylabdhub.dataitems;

import it.smartcommunitylabdhub.commons.services.EntityAwareService;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.commons.services.SchemaService;
import it.smartcommunitylabdhub.commons.services.SpecRegistry;
import it.smartcommunitylabdhub.core.events.EntityOperationsListener;
import it.smartcommunitylabdhub.core.services.EntityFinalizer;
import it.smartcommunitylabdhub.core.services.VersionableEntityService;
import it.smartcommunitylabdhub.files.service.EntityFilesService;
import it.smartcommunitylabdhub.lifecycle.LifecycleManager;
import it.smartcommunitylabdhub.relationships.RelationshipsAwareEntityService;
import it.smartcommunitylabdhub.search.service.IndexableEntityService;
import lombok.experimental.Delegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/*
 * Exposed manager for dataitem-related operations via services
 */
@Component
public class DataItemManager {

    @Delegate(excludes = { EntityAwareService.class })
    private EntityService<DataItem> dataitemEntityService;

    @Delegate(excludes = { EntityAwareService.class })
    private VersionableEntityService<DataItem> dataitemVersionableEntityService;

    @Delegate(excludes = { EntityAwareService.class })
    private EntityFilesService<DataItem> dataitemFilesService;

    @Delegate(excludes = { EntityAwareService.class })
    private IndexableEntityService<DataItem> dataitemIndexableEntityService;

    @Delegate(excludes = { EntityAwareService.class })
    private LifecycleManager<DataItem> dataitemLifecycleManager;

    @Delegate(excludes = { EntityAwareService.class })
    private RelationshipsAwareEntityService<DataItem> dataitemRelationshipsAwareEntityService;

    @Delegate(excludes = { EntityAwareService.class })
    private EntityFinalizer<DataItem> dataitemEntityFinalizer;

    @Delegate(excludes = { EntityAwareService.class })
    private EntityOperationsListener<DataItem> dataitemOperationsListener;

    @Delegate(excludes = { EntityAwareService.class })
    private SpecRegistry<DataItem> dataitemSpecRegistry;

    @Delegate(excludes = { EntityAwareService.class })
    private SchemaService<DataItem> dataitemSchemaService;

    @Autowired
    public void setDataItemEntityService(EntityService<DataItem> dataitemEntityService) {
        this.dataitemEntityService = dataitemEntityService;
    }

    @Autowired
    public void setDataItemVersionableEntityService(
        VersionableEntityService<DataItem> dataitemVersionableEntityService
    ) {
        this.dataitemVersionableEntityService = dataitemVersionableEntityService;
    }

    @Autowired
    public void setDataItemFilesService(EntityFilesService<DataItem> dataitemFilesService) {
        this.dataitemFilesService = dataitemFilesService;
    }

    @Autowired
    public void setDataItemIndexableEntityService(IndexableEntityService<DataItem> dataitemIndexableEntityService) {
        this.dataitemIndexableEntityService = dataitemIndexableEntityService;
    }

    @Autowired
    public void setDataItemLifecycleManager(LifecycleManager<DataItem> dataitemLifecycleManager) {
        this.dataitemLifecycleManager = dataitemLifecycleManager;
    }

    @Autowired
    public void setDataItemRelationshipsAwareEntityService(
        RelationshipsAwareEntityService<DataItem> dataitemRelationshipsAwareEntityService
    ) {
        this.dataitemRelationshipsAwareEntityService = dataitemRelationshipsAwareEntityService;
    }

    @Autowired
    public void setDataItemEntityFinalizer(EntityFinalizer<DataItem> dataitemEntityFinalizer) {
        this.dataitemEntityFinalizer = dataitemEntityFinalizer;
    }

    @Autowired
    public void setDataItemOperationsListener(EntityOperationsListener<DataItem> dataitemOperationsListener) {
        this.dataitemOperationsListener = dataitemOperationsListener;
    }

    @Autowired
    public void setDataItemSpecRegistry(SpecRegistry<DataItem> dataitemSpecRegistry) {
        this.dataitemSpecRegistry = dataitemSpecRegistry;
    }

    @Autowired
    public void setDataItemSchemaService(SchemaService<DataItem> dataitemSchemaService) {
        this.dataitemSchemaService = dataitemSchemaService;
    }
}
