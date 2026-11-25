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

package it.smartcommunitylabdhub.artifacts;

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
 * Exposed manager for artifact-related operations via services
 */
@Component
public class ArtifactManager {

    @Delegate(excludes = { EntityAwareService.class })
    private EntityService<Artifact> artifactEntityService;

    @Delegate(excludes = { EntityAwareService.class })
    private VersionableEntityService<Artifact> artifactVersionableEntityService;

    @Delegate(excludes = { EntityAwareService.class })
    private EntityFilesService<Artifact> artifactFilesService;

    @Delegate(excludes = { EntityAwareService.class })
    private IndexableEntityService<Artifact> artifactIndexableEntityService;

    @Delegate(excludes = { EntityAwareService.class })
    private LifecycleManager<Artifact> artifactLifecycleManager;

    @Delegate(excludes = { EntityAwareService.class })
    private RelationshipsAwareEntityService<Artifact> artifactRelationshipsAwareEntityService;

    @Delegate(excludes = { EntityAwareService.class })
    private EntityFinalizer<Artifact> artifactEntityFinalizer;

    @Delegate(excludes = { EntityAwareService.class })
    private EntityOperationsListener<Artifact> artifactOperationsListener;

    @Delegate(excludes = { EntityAwareService.class })
    private SpecRegistry<Artifact> artifactSpecRegistry;

    @Delegate(excludes = { EntityAwareService.class })
    private SchemaService<Artifact> artifactSchemaService;

    @Autowired
    public void setArtifactEntityService(EntityService<Artifact> artifactEntityService) {
        this.artifactEntityService = artifactEntityService;
    }

    @Autowired
    public void setArtifactVersionableEntityService(
        VersionableEntityService<Artifact> artifactVersionableEntityService
    ) {
        this.artifactVersionableEntityService = artifactVersionableEntityService;
    }

    @Autowired
    public void setArtifactFilesService(EntityFilesService<Artifact> artifactFilesService) {
        this.artifactFilesService = artifactFilesService;
    }

    @Autowired
    public void setArtifactIndexableEntityService(IndexableEntityService<Artifact> artifactIndexableEntityService) {
        this.artifactIndexableEntityService = artifactIndexableEntityService;
    }

    @Autowired
    public void setArtifactLifecycleManager(LifecycleManager<Artifact> artifactLifecycleManager) {
        this.artifactLifecycleManager = artifactLifecycleManager;
    }

    @Autowired
    public void setArtifactRelationshipsAwareEntityService(
        RelationshipsAwareEntityService<Artifact> artifactRelationshipsAwareEntityService
    ) {
        this.artifactRelationshipsAwareEntityService = artifactRelationshipsAwareEntityService;
    }

    @Autowired
    public void setArtifactEntityFinalizer(EntityFinalizer<Artifact> artifactEntityFinalizer) {
        this.artifactEntityFinalizer = artifactEntityFinalizer;
    }

    @Autowired
    public void setArtifactOperationsListener(EntityOperationsListener<Artifact> artifactOperationsListener) {
        this.artifactOperationsListener = artifactOperationsListener;
    }

    @Autowired
    public void setArtifactSpecRegistry(SpecRegistry<Artifact> artifactSpecRegistry) {
        this.artifactSpecRegistry = artifactSpecRegistry;
    }

    @Autowired
    public void setArtifactSchemaService(SchemaService<Artifact> artifactSchemaService) {
        this.artifactSchemaService = artifactSchemaService;
    }
}
