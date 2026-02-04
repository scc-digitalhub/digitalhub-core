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

package it.smartcommunitylabdhub.extensions.service;

import com.networknt.schema.ValidationMessage;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.core.queries.specifications.CommonSpecification;
import it.smartcommunitylabdhub.core.repositories.SearchableEntityRepository;
import it.smartcommunitylabdhub.core.services.EntityService;
import it.smartcommunitylabdhub.extensions.ExtensionManager;
import it.smartcommunitylabdhub.extensions.model.Extension;
import it.smartcommunitylabdhub.extensions.persistence.ExtensionEntity;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;

@Service
@Transactional
@Slf4j
public class ExtensionServiceImpl implements EntityService<Extension>, ExtensionManager {

    private final SearchableEntityRepository<ExtensionEntity, Extension> entityRepository;

    private ExtensionSchemaService schemaService;

    public ExtensionServiceImpl(SearchableEntityRepository<ExtensionEntity, Extension> entityRepository) {
        Assert.notNull(entityRepository, "entity repository is required");

        this.entityRepository = entityRepository;
    }

    @Autowired
    public void setSchemaService(ExtensionSchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @Override
    public Page<Extension> listExtensions(Pageable pageable) throws SystemException {
        try {
            return list(pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Extension> listExtensionsByEntity(String entity) throws SystemException {
        try {
            return entityRepository.searchAll(createEntitySpecification(entity));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Extension> listExtensionsByEntity(String entity, Pageable pageable) throws SystemException {
        try {
            return entityRepository.search(createEntitySpecification(entity), pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Extension> listExtensionsByProject(String project) throws SystemException {
        try {
            return listByProject(project);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Extension> listExtensionsByProject(String project, Pageable pageable) throws SystemException {
        try {
            return listByProject(project, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Extension> listExtensionsByProject(String project, String kind) throws SystemException {
        log.debug("list extensions for project {} and kind {} ", project, kind);

        try {
            return entityRepository.searchAll(
                Specification.allOf(CommonSpecification.projectEquals(project), CommonSpecification.kindEquals(kind))
            );
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Extension> listExtensionsByProject(String project, String kind, Pageable pageable)
        throws SystemException {
        log.debug("list extensions for project {} and kind {} ", project, kind);

        try {
            return entityRepository.search(
                Specification.allOf(CommonSpecification.projectEquals(project), CommonSpecification.kindEquals(kind)),
                pageable
            );
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Extension> listExtensionsByUser(String user) throws SystemException {
        try {
            return listByUser(user);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Extension> listExtensionsByUser(String user, Pageable pageable) throws SystemException {
        try {
            return listByUser(user, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Extension> listExtensionsByParent(String parent) throws SystemException {
        log.debug("list extensions for parent {} ", parent);

        try {
            return entityRepository.searchAll(createParentSpecification(parent));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Extension> listExtensionsByParent(String parent, String kind) throws SystemException {
        log.debug("list extensions for parent {} and kind {} ", parent, kind);

        try {
            return entityRepository.searchAll(
                Specification.allOf(createParentSpecification(parent), CommonSpecification.kindEquals(kind))
            );
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Extension> listExtensionsByParent(String parent, String kind, String name) throws SystemException {
        log.debug("list extensions for parent {} and kind {} and name {}", parent, kind, name);

        try {
            return entityRepository.searchAll(
                Specification.allOf(
                    createParentSpecification(parent),
                    CommonSpecification.kindEquals(kind),
                    CommonSpecification.nameEquals(name)
                )
            );
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Extension findExtension(String id) throws SystemException {
        try {
            return find(id);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Extension getExtension(String id) throws NoSuchEntityException, SystemException {
        try {
            return get(id);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Extension createExtension(Extension dto)
        throws DuplicatedEntityException, BindException, IllegalArgumentException, SystemException {
        try {
            return create(dto);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Extension createOrUpdateExtension(Extension dto)
        throws BindException, IllegalArgumentException, SystemException {
        try {
            String id = dto.getId();
            Extension e = null;
            if (StringUtils.hasText(id)) {
                e = find(id);
            }

            if (e != null) {
                return update(id, dto);
            } else {
                return create(dto);
            }
        } catch (StoreException | DuplicatedEntityException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Extension updateExtension(String id, Extension dto)
        throws NoSuchEntityException, BindException, IllegalArgumentException, SystemException {
        try {
            return update(id, dto);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteExtension(String id) throws SystemException {
        try {
            delete(id, null);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteExtensionsByParent(String parent) throws SystemException {
        log.debug("delete extensions with parent {}", parent);

        try {
            entityRepository.deleteAll(createParentSpecification(parent));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteExtensionsByProject(String project) throws SystemException {
        log.debug("delete extensions with project {}", project);

        try {
            deleteByProject(project, null);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteExtensionByEntity(@NotNull String entity) throws StoreException {
        log.debug("delete extensions by entity {}", entity);
        entityRepository.deleteAll(createEntitySpecification(entity));
    }

    @Override
    public void deleteExtensionByEntityAndUser(@NotNull String entity, @NotNull String user) throws StoreException {
        log.debug("delete extensions by entity {} and user {}", entity, user);
        entityRepository.deleteAll(
            Specification.allOf(createEntitySpecification(entity), CommonSpecification.createdByEquals(user))
        );
    }

    @Override
    public void deleteExtensionByEntityAndProject(@NotNull String entity, @NotNull String project)
        throws StoreException {
        log.debug("delete extensions by entity {} and project {}", entity, project);
        entityRepository.deleteAll(
            Specification.allOf(createEntitySpecification(entity), CommonSpecification.projectEquals(project))
        );
    }

    @Override
    public void deleteExtensionByEntityAndKind(@NotNull String entity, @NotNull String kind) throws StoreException {
        log.debug("delete extensions by entity {} and kind {}", entity, kind);
        entityRepository.deleteAll(
            Specification.allOf(createEntitySpecification(entity), CommonSpecification.kindEquals(kind))
        );
    }

    @Override
    public Class<Extension> getType() {
        return Extension.class;
    }

    @Override
    public Extension create(@NotNull Extension dto)
        throws BindException, IllegalArgumentException, DuplicatedEntityException, StoreException {
        log.debug("create extension {}", dto);
        if (log.isTraceEnabled()) {
            log.trace("dto: {}", dto);
        }

        //validate project
        String projectId = dto.getProject();
        if (!StringUtils.hasText(projectId)) {
            throw new IllegalArgumentException("invalid or missing project");
        }

        //validate entity
        String entityId = dto.getEntity();
        if (!StringUtils.hasText(entityId)) {
            throw new IllegalArgumentException("invalid or missing entity");
        }

        //validate parent
        String parentId = dto.getParent();
        if (!StringUtils.hasText(parentId)) {
            throw new IllegalArgumentException("invalid or missing parent");
        }

        if (schemaService != null) {
            try {
                // Parse and validate spec
                Map<String, Serializable> data = schemaService.createSpec(dto.getKind(), dto.getSpec());
                Set<ValidationMessage> errors = schemaService.validateSchema(dto.getKind(), data);
                if (!errors.isEmpty()) {
                    BindException bindException = new BindException(dto, "Extension");
                    for (ValidationMessage vm : errors) {
                        String field = vm.getPath().isEmpty() ? "spec" : "spec." + vm.getPath();
                        bindException.rejectValue(field, "invalid", vm.getMessage());
                    }
                    throw bindException;
                }

                //update spec as exported
                dto.setSpec(data);
            } catch (IOException iox) {
                log.error("schema validation error: {}", iox.getMessage());
                throw new StoreException("IO error: " + iox.getMessage());
            }
        }

        Extension extension = entityRepository.create(dto);

        if (log.isTraceEnabled()) {
            log.trace("res: {}", extension);
        }

        return extension;
    }

    @Override
    public Extension update(@NotNull String id, @NotNull Extension dto)
        throws BindException, IllegalArgumentException, NoSuchEntityException, StoreException {
        log.debug("update extension with id {}", id);
        if (log.isTraceEnabled()) {
            log.trace("dto: {}", dto);
        }

        //fetch current and merge
        Extension current = entityRepository.get(id);
        if (current == null) {
            throw new StoreException("Invalid or broken entity in store");
        }

        if (schemaService != null) {
            try {
                // Parse and validate spec
                Map<String, Serializable> data = schemaService.createSpec(dto.getKind(), dto.getSpec());
                Set<ValidationMessage> errors = schemaService.validateSchema(dto.getKind(), data);
                if (!errors.isEmpty()) {
                    BindException bindException = new BindException(dto, "Extension");
                    for (ValidationMessage vm : errors) {
                        String field = vm.getPath().isEmpty() ? "spec" : "spec." + vm.getPath();
                        bindException.rejectValue(field, "invalid", vm.getMessage());
                    }
                    throw bindException;
                }

                //update spec as exported
                dto.setSpec(data);
            } catch (IOException iox) {
                log.error("schema validation error: {}", iox.getMessage());
                throw new StoreException("IO error: " + iox.getMessage());
            }
        }

        //merge spec
        current.setSpec(dto.getSpec());

        //update
        Extension extension = entityRepository.update(id, dto);
        if (log.isTraceEnabled()) {
            log.trace("res: {}", extension);
        }

        return extension;
    }

    @Override
    public Extension update(@NotNull String id, @NotNull Extension dto, boolean forceUpdate)
        throws BindException, IllegalArgumentException, NoSuchEntityException, StoreException {
        log.debug("update extension with id {} (forceUpdate: {})", id, forceUpdate);
        return update(id, dto);
    }

    @Override
    public void delete(@NotNull String id, @Nullable Boolean cascade) throws StoreException {
        log.debug("delete extension with id {} (cascade: {})", id, cascade);
        entityRepository.delete(id);
    }

    @Override
    public void deleteAll(@Nullable Boolean cascade) throws StoreException {
        log.debug("delete all extensions (cascade: {})", cascade);
        entityRepository.deleteAll();
    }

    @Override
    public void deleteByUser(@NotNull String user, @Nullable Boolean cascade) throws StoreException {
        log.debug("delete extensions by user {} (cascade: {})", user, cascade);
        entityRepository.deleteAll(CommonSpecification.createdByEquals(user));
    }

    @Override
    public void deleteByProject(@NotNull String project, @Nullable Boolean cascade) throws StoreException {
        log.debug("delete extensions by project {} (cascade: {})", project, cascade);
        entityRepository.deleteAll(CommonSpecification.projectEquals(project));
    }

    @Override
    public void deleteByKind(@NotNull String kind, @Nullable Boolean cascade) throws StoreException {
        log.debug("delete extensions by kind {} (cascade: {})", kind, cascade);
        entityRepository.deleteAll(CommonSpecification.kindEquals(kind));
    }

    @Override
    public Extension find(@NotNull String id) throws StoreException {
        log.debug("find extension with id {}", id);
        return entityRepository.find(id);
    }

    @Override
    public Extension get(@NotNull String id) throws NoSuchEntityException, StoreException {
        log.debug("get extension with id {}", id);
        return entityRepository.get(id);
    }

    @Override
    public List<Extension> listAll() throws StoreException {
        log.debug("list all extensions");
        return entityRepository.searchAll((root, query, criteriaBuilder) -> criteriaBuilder.conjunction());
    }

    @Override
    public Page<Extension> list(Pageable page) throws StoreException {
        log.debug("list extensions page {}", page);
        return entityRepository.list(page);
    }

    @Override
    public List<Extension> listByUser(@NotNull String user) throws StoreException {
        log.debug("list extensions by user {}", user);
        return entityRepository.searchAll(CommonSpecification.createdByEquals(user));
    }

    @Override
    public Page<Extension> listByUser(@NotNull String user, Pageable page) throws StoreException {
        log.debug("list extensions by user {} page {}", user, page);
        return entityRepository.search(CommonSpecification.createdByEquals(user), page);
    }

    @Override
    public List<Extension> listByProject(@NotNull String project) throws StoreException {
        log.debug("list extensions by project {}", project);
        return entityRepository.searchAll(CommonSpecification.projectEquals(project));
    }

    @Override
    public Page<Extension> listByProject(@NotNull String project, Pageable page) throws StoreException {
        log.debug("list extensions by project {} page {}", project, page);
        return entityRepository.search(CommonSpecification.projectEquals(project), page);
    }

    @Override
    public List<Extension> listByKind(@NotNull String kind) throws StoreException {
        log.debug("list extensions by kind {}", kind);
        return entityRepository.searchAll(CommonSpecification.kindEquals(kind));
    }

    @Override
    public Page<Extension> listByKind(@NotNull String kind, Pageable page) throws StoreException {
        log.debug("list extensions by kind {} page {}", kind, page);
        return entityRepository.search(CommonSpecification.kindEquals(kind), page);
    }

    @Override
    public List<Extension> search(SearchFilter<Extension> filter) throws StoreException {
        log.debug("search extensions");
        throw new UnsupportedOperationException("Unimplemented method 'search'");
    }

    @Override
    public Page<Extension> search(SearchFilter<Extension> filter, Pageable page) throws StoreException {
        log.debug("search extensions page {}", page);
        throw new UnsupportedOperationException("Unimplemented method 'search'");
    }

    @Override
    public List<Extension> searchByProject(@NotNull String project, SearchFilter<Extension> filter)
        throws StoreException {
        log.debug("search extensions by project {}", project);
        throw new UnsupportedOperationException("Unimplemented method 'searchByProject'");
    }

    @Override
    public Page<Extension> searchByProject(@NotNull String project, SearchFilter<Extension> filter, Pageable page)
        throws StoreException {
        log.debug("search extensions by project {} page {}", project, page);
        throw new UnsupportedOperationException("Unimplemented method 'searchByProject'");
    }

    private Specification<ExtensionEntity> createParentSpecification(String parent) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("parent"), parent);
    }

    private Specification<ExtensionEntity> createEntitySpecification(String entity) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("entity"), entity);
    }
}
