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

package it.smartcommunitylabdhub.folder.services;

import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.metadata.MetadataDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.core.events.EntityOperationsListener;
import it.smartcommunitylabdhub.core.queries.filters.AbstractEntityFilter;
import it.smartcommunitylabdhub.core.queries.filters.BaseEntityFilter;
import it.smartcommunitylabdhub.core.services.EntityService;
import it.smartcommunitylabdhub.core.services.VersionableEntityService;
import it.smartcommunitylabdhub.files.models.TokenSlice;
import it.smartcommunitylabdhub.files.service.EntityFilesService;
import it.smartcommunitylabdhub.folder.Folder;
import it.smartcommunitylabdhub.folder.persistence.FolderEntry;
import it.smartcommunitylabdhub.folder.persistence.FolderEntryRepository;
import it.smartcommunitylabdhub.folder.specs.FolderMetadata;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ResolvableTypeProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.annotation.Validated;

@Slf4j
@Service
@Validated
public class FolderEntriesService implements InitializingBean {

    public static final int PAGE_SIZE = 100;
    public static final Sort DEFAULT_SORT = Sort.by(Direction.ASC, "name", "id");

    private final FolderEntryRepository folderEntriesRepository;
    private EntityRepository<Folder> folderRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Map<String, EntityService<?>> entityServices = new HashMap<>();
    private Map<String, EntityFilesService<?>> fileServices = new HashMap<>();
    private Map<String, VersionableEntityService<?>> versionableServices = new HashMap<>();

    public FolderEntriesService(FolderEntryRepository folderEntriesRepository) {
        Assert.notNull(folderEntriesRepository, "folderEntriesRepository is required");
        this.folderEntriesRepository = folderEntriesRepository;
    }

    @Autowired
    public void setEntityServices(List<EntityService<?>> entityServices) {
        for (EntityService<?> service : entityServices) {
            if (service instanceof ResolvableTypeProvider resolvableProvider) {
                Class<?> entityClass = resolvableProvider.getResolvableType().resolve();
                if (
                    entityClass != null &&
                    BaseDTO.class.isAssignableFrom(entityClass) &&
                    StatusDTO.class.isAssignableFrom(entityClass)
                ) {
                    log.debug(
                        "registering entity operations listener {} for entity class {}",
                        service.getClass().getSimpleName(),
                        entityClass.getSimpleName()
                    );
                    this.entityServices.put(entityClass.getSimpleName().toUpperCase(), service);
                }
            }
        }
    }

    @Autowired
    public void setFileServices(List<EntityFilesService<?>> fileServices) {
        for (EntityFilesService<?> service : fileServices) {
            if (service instanceof ResolvableTypeProvider resolvableProvider) {
                Class<?> entityClass = resolvableProvider.getResolvableType().resolve();
                if (
                    entityClass != null &&
                    BaseDTO.class.isAssignableFrom(entityClass) &&
                    StatusDTO.class.isAssignableFrom(entityClass)
                ) {
                    log.debug(
                        "registering file service {} for entity class {}",
                        service.getClass().getSimpleName(),
                        entityClass.getSimpleName()
                    );
                    this.fileServices.put(entityClass.getSimpleName().toUpperCase(), service);
                }
            }
        }
    }

    @Autowired
    public void setVersionableServices(List<VersionableEntityService<?>> versionableServices) {
        for (VersionableEntityService<?> service : versionableServices) {
            if (service instanceof ResolvableTypeProvider resolvableProvider) {
                Class<?> entityClass = resolvableProvider.getResolvableType().resolve();
                if (entityClass != null && BaseDTO.class.isAssignableFrom(entityClass)) {
                    log.debug(
                        "registering versionable service {} for entity class {}",
                        service.getClass().getSimpleName(),
                        entityClass.getSimpleName()
                    );
                    this.versionableServices.put(entityClass.getSimpleName().toUpperCase(), service);
                }
            }
        }
    }

    @Autowired
    public void setFolderRepository(EntityRepository<Folder> folderRepository) {
        this.folderRepository = folderRepository;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(folderRepository, "folder repository is required");
    }

    /*
     * Public API
     */

    public boolean isSupported(@NotNull Class<?> entityClass) {
        if (Folder.class.equals(entityClass)) {
            //folder is supported but not registered as entry
            return true;
        }

        //by default we support all entries with files
        return fileServices.containsKey(entityClass.getSimpleName().toUpperCase());
    }

    public boolean isSupported(@NotNull String entityClass) {
        if (Folder.class.getSimpleName().equalsIgnoreCase(entityClass)) {
            //folder is supported but not registered as entry
            return true;
        }

        //by default we support all entries with files
        return fileServices.containsKey(entityClass.toUpperCase());
    }

    public <T extends BaseDTO & MetadataDTO> FolderEntry registerEntry(
        @NotBlank String project,
        @Nullable String folderId,
        @NotNull T dto
    ) throws StoreException {
        log.debug("register entry {} for folder {}", dto.getKey(), String.valueOf(folderId));

        //check if supported
        if (!isSupported(dto.getClass())) {
            throw new IllegalArgumentException("unsupported entity class: " + dto.getClass().getName());
        }

        if (folderId != null) {
            //check folder exists
            Folder folder = folderRepository.find(folderId);
            if (folder == null) {
                throw new IllegalArgumentException("Folder not found: " + folderId);
            }

            if (!folder.getProject().equals(dto.getProject())) {
                throw new IllegalArgumentException("Folder and entry project mismatch");
            }
        }

        //if the entry is placed in a folder, reject any name collision from a different kind
        if (folderId != null) {
            List<FolderEntry> conflicting = folderEntriesRepository.findByProjectAndFolderIdAndNameAndKindNot(
                project,
                folderId,
                dto.getName(),
                dto.getKind()
            );
            if (conflicting != null && !conflicting.isEmpty()) {
                throw new IllegalArgumentException(
                    "Name conflict in folder " +
                        folderId +
                        ": name '" +
                        dto.getName() +
                        "' already used by a different kind"
                );
            }
        }

        //check entry with same name + kind exists for the same folder
        List<FolderEntry> existingEntries = folderEntriesRepository.findByProjectAndNameAndKind(
            project,
            dto.getName(),
            dto.getKind()
        );
        if (existingEntries != null && !existingEntries.isEmpty()) {
            //check if the entry is the same
            if (existingEntries.size() == 1) {
                FolderEntry existing = existingEntries.get(0);
                if (
                    existing.getId().equals(dto.getId()) &&
                    ((existing.getFolderId() == null && folderId == null) || existing.getFolderId().equals(folderId))
                ) {
                    //same entry, just return it
                    return existing;
                }
            }

            //remove, for now we support only a single entry to avoid versioning issues
            //TODO support linking or multiple entries with same name
            folderEntriesRepository.deleteAll(existingEntries);
        }

        //create entry
        FolderEntry entry = new FolderEntry();
        entry.setId(dto.getId());
        entry.setFolderId(folderId);
        entry.setProject(project);

        entry.setType(EntityUtils.getEntityName(dto.getClass()));
        entry.setKind(dto.getKind());
        entry.setName(dto.getName());
        //TODO size

        entry = folderEntriesRepository.saveAndFlush(entry);
        //always detach to avoid side effects
        entityManager.detach(entry);

        if (log.isTraceEnabled()) {
            log.trace("entry: {}", entry);
        }

        return entry;
    }

    public void deleteEntry(@NotBlank String id) throws StoreException {
        log.debug("delete entry {}", id);

        FolderEntry entry = folderEntriesRepository.findById(id).orElse(null);
        if (entry != null) {
            //delete the entry
            folderEntriesRepository.delete(entry);
        }
    }

    public void deleteAllEntriesByFolderId(@NotBlank String project, @NotBlank String folderId) throws StoreException {
        log.debug("delete entry by folderId {} for project {}", folderId, project);

        List<FolderEntry> entries = folderEntriesRepository.findByProjectAndFolderId(project, folderId);
        if (entries != null) {
            //delete *only* the entries
            folderEntriesRepository.deleteAll(entries);
        }
    }

    public void deleteAllEntries(@NotBlank String project, @NotBlank String name, @NotBlank String kind)
        throws StoreException {
        log.debug("delete entry by name {} and kind {} for project {}", name, kind, project);

        List<FolderEntry> entries = folderEntriesRepository.findByProjectAndNameAndKind(project, name, kind);
        if (entries != null) {
            //delete *only* the entries
            folderEntriesRepository.deleteAll(entries);
        }
    }

    public Slice<FolderEntry> listEntries(
        @NotBlank String project,
        @Nullable String folderId,
        Pageable pageable,
        @Nullable String token
    ) throws StoreException {
        log.debug("list entries for folder {} page {}", String.valueOf(folderId), pageable);
        if (log.isTraceEnabled()) {
            log.trace("last entry: {}", token);
        }
        if (folderId != null) {
            Folder folder = folderRepository.find(folderId);
            if (folder == null) {
                throw new IllegalArgumentException("Folder not found: " + folderId);
            }

            if (!folder.getProject().equals(project)) {
                throw new IllegalArgumentException("invalid project");
            }
        }

        int pageSize = Math.min(pageable != null ? pageable.getPageSize() : PAGE_SIZE, PAGE_SIZE);
        Sort sort =
            pageable != null && pageable.getSort() != null ? pageable.getSort().and(DEFAULT_SORT) : DEFAULT_SORT;

        //build specifications
        List<Specification<FolderEntry>> specs = new ArrayList<>();
        specs.add(projectEquals(project));
        //always filter by folderId, if null we will get only root entries
        specs.add(folderEquals(folderId));

        if (token != null) {
            specs.add(createOffsetSpecification(sort, token));
        }

        Specification<FolderEntry> where = Specification.allOf(specs);

        Page<FolderEntry> folderEntries = folderEntriesRepository.findAll(where, PageRequest.of(0, pageSize, sort));
        String nextToken = null;
        if (!folderEntries.isEmpty() && folderEntries.hasNext()) {
            //use last entry as next token: we need to encode all the sort properties to rebuild the filter
            FolderEntry last = folderEntries.getContent().get(folderEntries.getContent().size() - 1);
            nextToken = encodeToken(sort, last);
        }

        return new TokenSlice<>(folderEntries.getContent(), pageable, nextToken != null, nextToken);
    }

    public FolderEntry moveEntry(@NotBlank String project, @Nullable String folderId, @NotNull FolderEntry fe)
        throws StoreException {
        log.debug("move entry {} to folder {}", fe.getId(), String.valueOf(folderId));

        //check if supported
        if (!isSupported(fe.getType())) {
            throw new IllegalArgumentException("unsupported entity class: " + fe.getClass().getName());
        }

        // move means update the entity metadata.folderId to the destination, and directly register the new position
        // the listener will eventually receive the update but the registration is already aligned

        var entity = entityServices.get(fe.getType()).get(fe.getId());
        //check project match
        if (!entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        if (!(entity instanceof MetadataDTO)) {
            throw new IllegalArgumentException("entity does not support metadata");
        }

        var dto = (BaseDTO & MetadataDTO) entity;

        FolderMetadata metadata = FolderMetadata.from(dto.getMetadata());
        metadata.setFolderId(folderId);
        dto.setMetadata(MapUtils.mergeMultipleMaps(dto.getMetadata(), metadata.toMap()));

        // NOTE: we update the registration before to make sure it's valid, and THEN align
        FolderEntry entry = registerEntry(project, folderId, dto);
        if (entry == null) {
            throw new StoreException("Failed to register entry for " + fe.getId());
        }

        //update via service *ONLY* the folder
        try {
            invoke(entityServices.get(fe.getType()), dto, (svc, e) -> {
                try {
                    return svc.update(e.getId(), e);
                } catch (NoSuchEntityException | BindException | StoreException e1) {
                    throw new CoreRuntimeException("Failed to update entity " + e.getId(), e1);
                }
            });
        } catch (CoreRuntimeException e) {
            throw new StoreException("Failed to move entry " + fe.getId(), e);
        }

        return entry;
    }

    public void deleteEntry(@NotBlank String project, @NotNull FolderEntry fe) throws StoreException {
        log.debug("delete entry {} ", fe.getId());

        //check if supported
        if (!isSupported(fe.getType())) {
            throw new IllegalArgumentException("unsupported entity class: " + fe.getClass().getName());
        }

        var entity = entityServices.get(fe.getType()).find(fe.getId());

        if (entity != null) {
            //delete the entry with cascade, via name to remove all versions
            VersionableEntityService<?> versionable = versionableServices.get(fe.getType());
            if (versionable != null) {
                //delete all versions via versionable service (handles cascade, events, etc.)
                versionable.deleteAll(project, fe.getName(), true);
            } else {
                //fallback: delete only the single registered version by id
                entityServices.get(fe.getType()).delete(fe.getId(), true);
            }
        }

        //always remove the entry when present
        deleteEntry(fe.getId());
    }

    /*
     * Helpers
     */
    @SuppressWarnings("unchecked")
    private <D extends BaseDTO> D invoke(
        EntityService<D> service,
        BaseDTO dto,
        BiFunction<EntityService<D>, D, D> action
    ) {
        return action.apply(service, service.getType().cast(dto));
    }

    private Specification<FolderEntry> projectEquals(String project) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.equal(root.get("project"), project);
        };
    }

    private Specification<FolderEntry> folderEquals(String folderId) {
        if (StringUtils.hasText(folderId)) {
            return (root, query, criteriaBuilder) -> {
                return criteriaBuilder.equal(root.get("folderId"), folderId);
            };
        } else {
            //filter only root entries
            return (root, query, criteriaBuilder) -> {
                return criteriaBuilder.isNull(root.get("folderId"));
            };
        }
    }

    private Specification<FolderEntry> createOffsetSpecification(Sort sort, String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }

        //unpack
        List<Order> sorts = sort.toList();
        Map<String, String> entry = decodeToken(token);
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = sorts
                .stream()
                .map(order -> {
                    String property = order.getProperty();
                    Object value = entry.get(property);
                    if (value == null) {
                        return null;
                    }
                    if (order.isAscending()) {
                        return criteriaBuilder.greaterThan(root.get(property), (Comparable) value);
                    } else {
                        return criteriaBuilder.lessThan(root.get(property), (Comparable) value);
                    }
                })
                .filter(p -> p != null)
                .toList();
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String encodeToken(Sort sort, FolderEntry entry) {
        log.debug("encode entry {}", entry.getId());
        if (log.isTraceEnabled()) {
            log.trace("entry: {}", entry);
        }
        Map<String, Serializable> map = JacksonMapper.OBJECT_MAPPER.convertValue(entry, JacksonMapper.typeRef);

        List<Order> sorts = sort.toList();
        StringBuilder sb = new StringBuilder();
        for (Order order : sorts) {
            if (!sb.isEmpty()) {
                sb.append("|");
            }
            sb.append(order.getProperty());
            sb.append(":");
            sb.append(order.getDirection().name());
            sb.append("=");
            sb.append(map.get(order.getProperty()));
        }

        if (!StringUtils.hasText(sb.toString())) {
            sb.append("id:ASC=");
            sb.append(entry.getId());
        }

        String token = Base64.getUrlEncoder().encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
        log.debug("encoded token {}", token);
        return token;
    }

    private Map<String, String> decodeToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }

        log.debug("decode token {}", token);

        String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        String[] parts = decoded.split("\\|");
        Map<String, String> map = new HashMap<>();

        for (String part : parts) {
            String[] kv = part.split("=");
            if (kv.length != 2) {
                throw new IllegalArgumentException("invalid token");
            }
            String[] propDir = kv[0].split(":");
            if (propDir.length != 2) {
                throw new IllegalArgumentException("invalid token");
            }
            String prop = propDir[0];
            String dir = propDir[1];
            String value = kv[1];

            map.put(prop, value);
        }

        // FolderEntry entry = JacksonMapper.OBJECT_MAPPER.convertValue(map, FolderEntry.class);
        if (log.isTraceEnabled()) {
            log.trace("decoded entry: {}", map);
        }
        return map;
    }
}
