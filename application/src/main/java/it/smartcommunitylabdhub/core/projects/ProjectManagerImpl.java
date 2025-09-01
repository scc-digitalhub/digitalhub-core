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

package it.smartcommunitylabdhub.core.projects;

import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.artifact.Artifact;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.dataitem.DataItem;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.metadata.EmbeddableMetadata;
import it.smartcommunitylabdhub.commons.models.metadata.MetadataDTO;
import it.smartcommunitylabdhub.commons.models.model.Model;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.workflow.Workflow;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.commons.services.ProjectManager;
import it.smartcommunitylabdhub.commons.services.VersionableEntityService;
import it.smartcommunitylabdhub.commons.utils.EmbedUtils;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.core.components.infrastructure.specs.SpecValidator;
import it.smartcommunitylabdhub.core.projects.persistence.ProjectEntity;
import it.smartcommunitylabdhub.core.projects.specs.ProjectSpec;
import it.smartcommunitylabdhub.core.queries.specifications.CommonSpecification;
import it.smartcommunitylabdhub.core.repositories.SearchableEntityRepository;
import it.smartcommunitylabdhub.core.utils.RefUtils;
import it.smartcommunitylabdhub.relationships.EntityRelationshipsService;
import it.smartcommunitylabdhub.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.relationships.RelationshipsAwareEntityService;
import it.smartcommunitylabdhub.relationships.persistence.RelationshipEntity;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;

@Service
@Transactional
@Slf4j
public class ProjectManagerImpl implements ProjectManager, RelationshipsAwareEntityService<Project> {

    @Autowired
    private ApplicationProperties applicationProperties;

    @Autowired
    private EntityService<Project> entityService;

    @Autowired
    private SearchableEntityRepository<ProjectEntity, Project> entityRepository;

    //TODO evaluate removing embedding
    @Autowired
    private VersionableEntityService<Function> functionService;

    @Autowired
    private VersionableEntityService<Artifact> artifactService;

    @Autowired
    private VersionableEntityService<DataItem> dataItemService;

    @Autowired
    private VersionableEntityService<Model> modelService;

    @Autowired
    private VersionableEntityService<Workflow> workflowService;

    @Autowired
    private EntityRelationshipsService relationshipsService;

    @Autowired
    private SpecValidator validator;

    @Override
    public Page<Project> listProjects(Pageable pageable) {
        log.debug("list projects page {}", pageable);
        try {
            return entityService.list(pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Project> listProjectsByUser(@NotNull String user) {
        log.debug("list all projects for user {}", user);
        try {
            return entityService.listByUser(user);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Project> searchProjects(Pageable pageable, @Nullable SearchFilter<Project> filter) {
        log.debug("list projects page {}, filter {}", pageable, String.valueOf(filter));
        try {
            return entityService.search(filter, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Project findProjectByName(@NotNull String name) {
        log.debug("find project by name {}", name);
        try {
            return entityRepository.searchAll(CommonSpecification.nameEquals(name)).stream().findFirst().orElse(null);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Project findProject(@NotNull String id) {
        log.debug("find project with id {}", String.valueOf(id));
        try {
            return entityService.find(id);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Project getProject(@NotNull String id) throws NoSuchEntityException {
        log.debug("get project with id {}", String.valueOf(id));

        try {
            Project project = entityService.get(id);

            //load content
            log.debug("load project content for project {}", String.valueOf(id));

            List<Artifact> artifacts = artifactService.listLatestByProject(id);
            List<DataItem> dataItems = dataItemService.listLatestByProject(id);
            List<Model> models = modelService.listLatestByProject(id);
            List<Function> functions = functionService.listLatestByProject(id);
            List<Workflow> workflows = workflowService.listLatestByProject(id);

            //update spec
            ProjectSpec spec = new ProjectSpec();
            spec.configure(project.getSpec());

            //embed + ref
            spec.setArtifacts(
                artifacts.stream().map(EmbedUtils::embed).map(this::inlineRef).collect(Collectors.toList())
            );
            spec.setDataitems(
                dataItems.stream().map(EmbedUtils::embed).map(this::inlineRef).collect(Collectors.toList())
            );
            spec.setModels(models.stream().map(EmbedUtils::embed).map(this::inlineRef).collect(Collectors.toList()));
            spec.setFunctions(
                functions.stream().map(EmbedUtils::embed).map(this::inlineRef).collect(Collectors.toList())
            );
            spec.setWorkflows(
                workflows.stream().map(EmbedUtils::embed).map(this::inlineRef).collect(Collectors.toList())
            );

            project.setSpec(spec.toMap());

            return project;
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.PROJECT.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Project getProjectByName(@NotNull String name) throws NoSuchEntityException {
        log.debug("get project by name {}", name);
        try {
            Project project = entityRepository
                .searchAll(CommonSpecification.nameEquals(name))
                .stream()
                .findFirst()
                .orElseThrow(() -> new NoSuchEntityException(EntityName.PROJECT.toString()));

            return getProject(project.getId());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    @CacheEvict(
        value = {
            "findIdByCreatedBy",
            "findIdByUpdatedBy",
            "findIdByProject",
            "findNameByCreatedBy",
            "findNameByUpdatedBy",
            "findNameByProject",
        },
        allEntries = true
    )
    public Project createProject(@NotNull Project dto)
        throws DuplicatedEntityException, BindException, IllegalArgumentException {
        log.debug("create project");

        try {
            //check if a project for this name already exists
            String name = dto.getName();
            if (!StringUtils.hasText(name)) {
                throw new IllegalArgumentException("invalid or missing name");
            }

            Project existing = findProjectByName(dto.getName());
            if (existing != null) {
                throw new DuplicatedEntityException(EntityName.PROJECT.toString(), name);
            }

            //make sure project id matches name (for slugs)
            if (StringUtils.hasText(dto.getId()) && !dto.getName().equals(dto.getId())) {
                throw new IllegalArgumentException("project id must match name");
            }

            //enforce
            dto.setId(name);

            //create as new
            return entityService.create(dto);
        } catch (DuplicatedEntityException e) {
            throw new DuplicatedEntityException(EntityName.PROJECT.toString(), dto.getId());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Project updateProject(@NotNull String id, @NotNull Project dto)
        throws NoSuchEntityException, BindException, IllegalArgumentException {
        log.debug("update project with id {}", String.valueOf(id));
        try {
            // Parse and export Spec
            ProjectSpec spec = new ProjectSpec();
            spec.configure(dto.getSpec());

            //validate
            validator.validateSpec(spec);

            //update spec as exported
            dto.setSpec(spec.toMap());

            //full update, project is modifiable
            return entityService.update(id, dto, true);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.PROJECT.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    @CacheEvict(
        value = {
            "findIdByCreatedBy",
            "findIdByUpdatedBy",
            "findIdByProject",
            "findNameByCreatedBy",
            "findNameByUpdatedBy",
            "findNameByProject",
            "findIdsBySharedTo",
            "findNamesBySharedTo",
        },
        allEntries = true
    )
    public void deleteProject(@NotNull String id, @Nullable Boolean cascade) {
        log.debug("delete project with id {}", String.valueOf(id));
        try {
            //delete the project with cascade
            entityService.delete(id, cascade);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<RelationshipDetail> getRelationships(@NotNull String id) {
        log.debug("list relationships for project with id {}", String.valueOf(id));
        try {
            //fully load project
            Project project = getProject(id);
            ProjectSpec spec = new ProjectSpec();
            spec.configure(project.getSpec());

            //load all relationships
            List<RelationshipEntity> list = relationshipsService.listByProject(id);

            //filter for elements embedded in project
            List<String> ids = new ArrayList<>();
            Optional.ofNullable(spec.getArtifacts()).ifPresent(artifacts -> artifacts.forEach(a -> ids.add(a.getId())));
            Optional.ofNullable(spec.getDataitems()).ifPresent(dataItems -> dataItems.forEach(d -> ids.add(d.getId())));
            Optional.ofNullable(spec.getFunctions()).ifPresent(functions -> functions.forEach(f -> ids.add(f.getId())));
            Optional.ofNullable(spec.getModels()).ifPresent(models -> models.forEach(m -> ids.add(m.getId())));
            Optional.ofNullable(spec.getWorkflows()).ifPresent(workflows -> workflows.forEach(w -> ids.add(w.getId())));

            return list
                .stream()
                .filter(e -> ids.contains(e.getSourceId()) || ids.contains(e.getDestId()))
                .map(e -> new RelationshipDetail(e.getRelationship(), e.getSourceKey(), e.getDestKey()))
                .toList();
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    //TODO add to all services
    private <T extends BaseDTO & MetadataDTO> T inlineRef(T d) {
        String applicationUrl = applicationProperties.getEndpoint();
        String api = applicationProperties.getApi();

        if (applicationUrl != null && api != null) {
            String ref = RefUtils.getRefPath(d);
            if (ref != null) {
                EmbeddableMetadata em = EmbeddableMetadata.from(d.getMetadata());
                em.setRef(applicationUrl + "/api/" + api + ref);
                d.setMetadata(MapUtils.mergeMultipleMaps(d.getMetadata(), em.toMap()));
            }
        }

        return d;
    }
}
