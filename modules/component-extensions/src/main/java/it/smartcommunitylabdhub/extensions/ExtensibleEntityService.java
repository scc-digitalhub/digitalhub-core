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

package it.smartcommunitylabdhub.extensions;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.core.services.EntityService;
import it.smartcommunitylabdhub.extensions.model.ExtensibleDTO;
import it.smartcommunitylabdhub.extensions.model.Extension;
import it.smartcommunitylabdhub.extensions.persistence.ExtensionBuilder;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.validation.BindException;

@Slf4j
@Transactional
public class ExtensibleEntityService<D extends ExtensibleDTO & BaseDTO> implements EntityService<D>, InitializingBean {

    @JsonIgnore
    protected static final ObjectMapper mapper = JacksonMapper.CUSTOM_OBJECT_MAPPER;

    @JsonIgnore
    protected static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};

    private final EntityService<D> delegate;

    private ExtensionManager extManager;

    public ExtensibleEntityService(EntityService<D> delegate) {
        this.delegate = delegate;
    }

    @Autowired
    public void setExtensionManager(ExtensionManager extensionManager) {
        this.extManager = extensionManager;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(extManager, "extension manager is required");
    }

    @Override
    public Class<D> getType() {
        return delegate.getType();
    }

    @Override
    public D create(@NotNull D dto)
        throws BindException, IllegalArgumentException, DuplicatedEntityException, StoreException {
        D res = delegate.create(dto);

        //extract and create extensions
        //NOTE: we keep definition from source dto
        if (dto.getExtensions() != null) {
            log.debug("create extensions for dto with id {}", String.valueOf(dto.getId()));
            if (log.isTraceEnabled()) {
                log.trace("extensions: {}", dto.getExtensions());
            }

            dto
                .getExtensions()
                .forEach(e -> {
                    try {
                        Extension ed = mapper.convertValue(e, Extension.class);
                        Extension ext = ExtensionBuilder.from(res);
                        ext.setSpec(ed.getSpec());
                        ext.setEntity(EntityUtils.getEntityName(getType()));

                        extManager.createExtension(ext);
                    } catch (
                        DuplicatedEntityException | BindException | IllegalArgumentException | SystemException ex
                    ) {
                        log.error(
                            "error creating extension for entity {}: {}",
                            String.valueOf(res.getId()),
                            ex.getMessage()
                        );
                    }
                });
        }

        return res;
    }

    @Override
    public D update(@NotNull String id, @NotNull D dto)
        throws BindException, IllegalArgumentException, NoSuchEntityException, StoreException {
        D res = delegate.update(id, dto);

        //extract and create extensions
        //NOTE: we keep definition from source dto
        if (dto.getExtensions() != null) {
            log.debug("update extensions for dto with id {}", String.valueOf(dto.getId()));
            if (log.isTraceEnabled()) {
                log.trace("extensions: {}", dto.getExtensions());
            }

            dto
                .getExtensions()
                .forEach(e -> {
                    try {
                        Extension ed = mapper.convertValue(e, Extension.class);
                        Extension ext = ExtensionBuilder.from(res);
                        ext.setSpec(ed.getSpec());
                        ext.setEntity(EntityUtils.getEntityName(getType()));

                        extManager.createOrUpdateExtension(ext);
                    } catch (BindException | IllegalArgumentException | SystemException ex) {
                        log.error(
                            "error creating extension for entity {}: {}",
                            String.valueOf(res.getId()),
                            ex.getMessage()
                        );
                    }
                });
        }

        return res;
    }

    @Override
    public D update(@NotNull String id, @NotNull D dto, boolean forceUpdate)
        throws BindException, IllegalArgumentException, NoSuchEntityException, StoreException {
        D res = delegate.update(id, dto, forceUpdate);

        //extract and create extensions
        //NOTE: we keep definition from source dto
        if (dto.getExtensions() != null) {
            log.debug("update extensions for dto with id {}", String.valueOf(dto.getId()));
            if (log.isTraceEnabled()) {
                log.trace("extensions: {}", dto.getExtensions());
            }

            dto
                .getExtensions()
                .forEach(e -> {
                    try {
                        Extension ed = mapper.convertValue(e, Extension.class);
                        Extension ext = ExtensionBuilder.from(res);
                        ext.setSpec(ed.getSpec());
                        ext.setEntity(EntityUtils.getEntityName(getType()));

                        extManager.createOrUpdateExtension(ext);
                    } catch (BindException | IllegalArgumentException | SystemException ex) {
                        log.error(
                            "error creating extension for entity {}: {}",
                            String.valueOf(res.getId()),
                            ex.getMessage()
                        );
                    }
                });
        }

        return res;
    }

    @Override
    public void delete(@NotNull String id, @Nullable Boolean cascade) throws StoreException {
        delegate.delete(id, cascade);
        //cleanup extensions
        try {
            extManager.deleteExtensionByEntityAndProject(EntityUtils.getEntityName(getType()), null);
        } catch (StoreException e) {
            log.error("error cleaning up extensions for id {}: {}", id, e.getMessage());
        }
    }

    @Override
    public void deleteAll(@Nullable Boolean cascade) throws StoreException {
        delegate.deleteAll(cascade);
        //cleanup extensions
        try {
            extManager.deleteExtensionByEntity(EntityUtils.getEntityName(getType()));
        } catch (StoreException e) {
            log.error("error cleaning up extensions: {}", e.getMessage());
        }
    }

    @Override
    public void deleteByUser(@NotNull String user, @Nullable Boolean cascade) throws StoreException {
        delegate.deleteByUser(user, cascade);
        //cleanup extensions
        try {
            extManager.deleteExtensionByEntityAndUser(EntityUtils.getEntityName(getType()), user);
        } catch (StoreException e) {
            log.error("error cleaning up extensions for user {}: {}", user, e.getMessage());
        }
    }

    @Override
    public void deleteByProject(@NotNull String project, @Nullable Boolean cascade) throws StoreException {
        delegate.deleteByProject(project, cascade);
        //cleanup extensions
        try {
            extManager.deleteExtensionByEntityAndProject(EntityUtils.getEntityName(getType()), project);
        } catch (StoreException e) {
            log.error("error cleaning up extensions for project {}: {}", project, e.getMessage());
        }
    }

    @Override
    public void deleteByKind(@NotNull String kind, @Nullable Boolean cascade) throws StoreException {
        delegate.deleteByKind(kind, cascade);
        //cleanup extensions
        try {
            extManager.deleteExtensionByEntityAndKind(EntityUtils.getEntityName(getType()), kind);
        } catch (StoreException e) {
            log.error("error cleaning up extensions for kind {}: {}", kind, e.getMessage());
        }
    }

    @Override
    public D find(@NotNull String id) throws StoreException {
        return delegate.find(id);
    }

    @Override
    public D get(@NotNull String id) throws NoSuchEntityException, StoreException {
        D dto = delegate.get(id);

        // fetch and populate extensions
        try {
            String entityName = EntityUtils.getEntityName(getType());
            String parentId = id;

            List<Extension> extensions = extManager.listExtensionsByParent(parentId);

            if (extensions != null && !extensions.isEmpty()) {
                // filter by entity and convert to List<Map<String, Serializable>>
                List<Map<String, Serializable>> extensionMaps = extensions
                    .stream()
                    .filter(e -> entityName.equals(e.getEntity()))
                    .map(e -> mapper.convertValue(e, typeRef))
                    .collect(Collectors.toList());

                dto.setExtensions(extensionMaps);
            }
        } catch (SystemException e) {
            log.error("error fetching extensions for id {}: {}", id, e.getMessage());
        }

        return dto;
    }

    @Override
    public List<D> listAll() throws StoreException {
        return delegate.listAll();
    }

    @Override
    public Page<D> list(Pageable page) throws StoreException {
        return delegate.list(page);
    }

    @Override
    public List<D> listByUser(@NotNull String user) throws StoreException {
        return delegate.listByUser(user);
    }

    @Override
    public Page<D> listByUser(@NotNull String user, Pageable page) throws StoreException {
        return delegate.listByUser(user, page);
    }

    @Override
    public List<D> listByProject(@NotNull String project) throws StoreException {
        return delegate.listByProject(project);
    }

    @Override
    public Page<D> listByProject(@NotNull String project, Pageable page) throws StoreException {
        return delegate.listByProject(project, page);
    }

    @Override
    public List<D> listByKind(@NotNull String kind) throws StoreException {
        return delegate.listByKind(kind);
    }

    @Override
    public Page<D> listByKind(@NotNull String kind, Pageable page) throws StoreException {
        return delegate.listByKind(kind, page);
    }

    @Override
    public List<D> search(SearchFilter<D> filter) throws StoreException {
        return delegate.search(filter);
    }

    @Override
    public Page<D> search(SearchFilter<D> filter, Pageable page) throws StoreException {
        return delegate.search(filter, page);
    }

    @Override
    public List<D> searchByProject(@NotNull String project, SearchFilter<D> filter) throws StoreException {
        return delegate.searchByProject(project, filter);
    }

    @Override
    public Page<D> searchByProject(@NotNull String project, SearchFilter<D> filter, Pageable page)
        throws StoreException {
        return delegate.searchByProject(project, filter, page);
    }
}
