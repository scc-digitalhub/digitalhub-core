package it.smartcommunitylabdhub.core.dataitems.services;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.CredentialsService;
import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.models.dataitem.DataItem;
import it.smartcommunitylabdhub.commons.models.dataitem.DataItemBaseSpec;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.files.FileInfo;
import it.smartcommunitylabdhub.commons.models.files.FilesInfo;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.services.FilesInfoService;
import it.smartcommunitylabdhub.commons.services.RelationshipsAwareEntityService;
import it.smartcommunitylabdhub.commons.services.SpecRegistry;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.core.components.infrastructure.specs.SpecValidator;
import it.smartcommunitylabdhub.core.components.security.UserAuthenticationHelper;
import it.smartcommunitylabdhub.core.dataitems.builders.DataItemEntityBuilder;
import it.smartcommunitylabdhub.core.dataitems.lifecycle.DataItemLifecycleManager;
import it.smartcommunitylabdhub.core.dataitems.persistence.DataItemEntity;
import it.smartcommunitylabdhub.core.dataitems.relationships.DataItemEntityRelationshipsManager;
import it.smartcommunitylabdhub.core.dataitems.specs.DataItemBaseStatus;
import it.smartcommunitylabdhub.core.models.entities.AbstractEntity_;
import it.smartcommunitylabdhub.core.models.indexers.EntityIndexer;
import it.smartcommunitylabdhub.core.models.indexers.IndexableEntityService;
import it.smartcommunitylabdhub.core.models.queries.specifications.CommonSpecification;
import it.smartcommunitylabdhub.core.projects.persistence.ProjectEntity;
import it.smartcommunitylabdhub.core.services.EntityService;
import it.smartcommunitylabdhub.files.models.DownloadInfo;
import it.smartcommunitylabdhub.files.models.UploadInfo;
import it.smartcommunitylabdhub.files.service.EntityFilesService;
import it.smartcommunitylabdhub.files.service.FilesService;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;

@Service
@Transactional
@Slf4j
public class DataItemServiceImpl
    implements
        SearchableDataItemService,
        IndexableEntityService<DataItemEntity>,
        EntityFilesService<DataItem>,
        RelationshipsAwareEntityService<DataItem> {

    @Autowired
    private EntityService<DataItem, DataItemEntity> entityService;

    @Autowired
    private EntityService<Project, ProjectEntity> projectService;

    @Autowired(required = false)
    private EntityIndexer<DataItemEntity> indexer;

    @Autowired
    private DataItemEntityBuilder entityBuilder;

    @Autowired
    private SpecRegistry specRegistry;

    @Autowired
    private SpecValidator validator;

    @Autowired
    private FilesService filesService;

    @Autowired
    private FilesInfoService filesInfoService;

    @Autowired
    private DataItemEntityRelationshipsManager relationshipsManager;

    @Autowired
    private CredentialsService credentialsService;

    @Autowired
    private DataItemLifecycleManager lifecycleManager;

    @Override
    public Page<DataItem> listDataItems(Pageable pageable) {
        log.debug("list dataItems page {}", pageable);
        try {
            return entityService.list(pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<DataItem> listLatestDataItems() {
        log.debug("list latest dataItems");
        Specification<DataItemEntity> specification = CommonSpecification.latest();

        try {
            return entityService.searchAll(specification);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<DataItem> listLatestDataItems(Pageable pageable) {
        log.debug("list latest dataItems page {}", pageable);
        Specification<DataItemEntity> specification = CommonSpecification.latest();
        try {
            return entityService.search(specification, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<DataItem> listDataItemsByUser(@NotNull String user) {
        log.debug("list all dataItems for user {}", user);
        try {
            return entityService.searchAll(CommonSpecification.createdByEquals(user));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<DataItem> searchDataItems(Pageable pageable, SearchFilter<DataItemEntity> filter) {
        log.debug("list dataItems page {}, filter {}", pageable, String.valueOf(filter));
        try {
            Specification<DataItemEntity> specification = filter != null ? filter.toSpecification() : null;
            if (specification != null) {
                return entityService.search(specification, pageable);
            } else {
                return entityService.list(pageable);
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<DataItem> searchLatestDataItems(Pageable pageable, @Nullable SearchFilter<DataItemEntity> filter) {
        log.debug("search latest dataItems with {} page {}", String.valueOf(filter), pageable);
        Specification<DataItemEntity> filterSpecification = filter != null ? filter.toSpecification() : null;
        Specification<DataItemEntity> specification = Specification.allOf(
            CommonSpecification.latest(),
            filterSpecification
        );
        try {
            return entityService.search(specification, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<DataItem> listDataItemsByProject(@NotNull String project) {
        log.debug("list all dataItems for project {}", project);
        Specification<DataItemEntity> specification = Specification.allOf(CommonSpecification.projectEquals(project));
        try {
            return entityService.searchAll(specification);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<DataItem> listDataItemsByProject(@NotNull String project, Pageable pageable) {
        log.debug("list all dataItems for project {} page {}", project, pageable);
        Specification<DataItemEntity> specification = Specification.allOf(CommonSpecification.projectEquals(project));
        try {
            return entityService.search(specification, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<DataItem> listLatestDataItemsByProject(@NotNull String project) {
        log.debug("list latest dataItems for project {}", project);
        Specification<DataItemEntity> specification = Specification.allOf(
            CommonSpecification.projectEquals(project),
            CommonSpecification.latestByProject(project)
        );
        try {
            return entityService.searchAll(specification);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<DataItem> listLatestDataItemsByProject(@NotNull String project, Pageable pageable) {
        log.debug("list latest dataItems for project {} page {}", project, pageable);
        Specification<DataItemEntity> specification = Specification.allOf(
            CommonSpecification.projectEquals(project),
            CommonSpecification.latestByProject(project)
        );
        try {
            return entityService.search(specification, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<DataItem> searchDataItemsByProject(
        @NotNull String project,
        Pageable pageable,
        SearchFilter<DataItemEntity> filter
    ) {
        log.debug("search all dataItems for project {} with {} page {}", project, String.valueOf(filter), pageable);
        Specification<DataItemEntity> filterSpecification = filter != null ? filter.toSpecification() : null;
        Specification<DataItemEntity> specification = Specification.allOf(
            CommonSpecification.projectEquals(project),
            filterSpecification
        );
        try {
            return entityService.search(specification, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<DataItem> searchLatestDataItemsByProject(
        @NotNull String project,
        Pageable pageable,
        SearchFilter<DataItemEntity> filter
    ) {
        log.debug("search latest dataItems for project {} with {} page {}", project, String.valueOf(filter), pageable);
        Specification<DataItemEntity> filterSpecification = filter != null ? filter.toSpecification() : null;
        Specification<DataItemEntity> specification = Specification.allOf(
            CommonSpecification.projectEquals(project),
            CommonSpecification.latestByProject(project),
            filterSpecification
        );
        try {
            return entityService.search(specification, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<DataItem> findDataItems(@NotNull String project, @NotNull String name) {
        log.debug("find dataItems for project {} with name {}", project, name);

        //fetch all versions ordered by date DESC
        Specification<DataItemEntity> where = Specification.allOf(
            CommonSpecification.projectEquals(project),
            CommonSpecification.nameEquals(name)
        );

        Specification<DataItemEntity> specification = (root, query, builder) -> {
            query.orderBy(builder.desc(root.get(AbstractEntity_.CREATED)));
            return where.toPredicate(root, query, builder);
        };
        try {
            return entityService.searchAll(specification);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<DataItem> findDataItems(@NotNull String project, @NotNull String name, Pageable pageable) {
        log.debug("find dataItems for project {} with name {} page {}", project, name, pageable);

        //fetch all versions ordered by date DESC
        Specification<DataItemEntity> where = Specification.allOf(
            CommonSpecification.projectEquals(project),
            CommonSpecification.nameEquals(name)
        );
        Specification<DataItemEntity> specification = (root, query, builder) -> {
            query.orderBy(builder.desc(root.get(AbstractEntity_.CREATED)));
            return where.toPredicate(root, query, builder);
        };
        try {
            return entityService.search(specification, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public DataItem findDataItem(@NotNull String id) {
        log.debug("find dataItem with id {}", String.valueOf(id));
        try {
            return entityService.find(id);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public DataItem getDataItem(@NotNull String id) throws NoSuchEntityException {
        log.debug("get dataItem with id {}", String.valueOf(id));

        try {
            return entityService.get(id);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.DATAITEM.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public DataItem getLatestDataItem(@NotNull String project, @NotNull String name) throws NoSuchEntityException {
        log.debug("get latest dataItem for project {} with name {}", project, name);
        try {
            //fetch latest version ordered by date DESC
            Specification<DataItemEntity> specification = CommonSpecification.latestByProject(project, name);
            return entityService.searchAll(specification).stream().findFirst().orElseThrow(NoSuchEntityException::new);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public DataItem createDataItem(@NotNull DataItem dto)
        throws DuplicatedEntityException, BindException, IllegalArgumentException {
        log.debug("create dataItem");
        if (log.isTraceEnabled()) {
            log.trace("dto: {}", dto);
        }
        try {
            //validate project
            String projectId = dto.getProject();
            if (!StringUtils.hasText(projectId) || projectService.find(projectId) == null) {
                throw new IllegalArgumentException("invalid or missing project");
            }

            // Parse and export Spec
            Spec spec = specRegistry.createSpec(dto.getKind(), dto.getSpec());
            if (spec == null) {
                throw new IllegalArgumentException("invalid kind");
            }

            //validate
            validator.validateSpec(spec);

            //update spec as exported
            dto.setSpec(spec.toMap());

            //on create status is *always* CREATED
            //keep the user provided and move via lifecycle if needed
            DataItemBaseStatus status = DataItemBaseStatus.with(dto.getStatus());
            State nextState = status.getState() == null ? State.CREATED : State.valueOf(status.getState());

            status.setState(nextState.name());
            dto.setStatus(MapUtils.mergeMultipleMaps(dto.getStatus(), status.toMap()));

            try {
                if (log.isTraceEnabled()) {
                    log.trace("storable dto: {}", dto);
                }

                //persist to store
                dto = entityService.create(dto);

                //perform transition if needed
                if (nextState != State.CREATED) {
                    dto = lifecycleManager.handle(dto, nextState);
                }

                return dto;
            } catch (DuplicatedEntityException e) {
                throw new DuplicatedEntityException(EntityName.DATAITEM.toString(), dto.getId());
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public DataItem updateDataItem(@NotNull String id, @NotNull DataItem dto)
        throws NoSuchEntityException, BindException, IllegalArgumentException {
        log.debug("dataItem dataItem with id {}", String.valueOf(id));
        try {
            //fetch current and merge
            DataItem current = entityService.get(id);
            DataItemBaseStatus curStatus = DataItemBaseStatus.with(current.getStatus());
            //we assume that missing status means CREATED
            State currentState = curStatus.getState() == null ? State.CREATED : State.valueOf(curStatus.getState());

            //spec is not modificable: enforce current
            dto.setSpec(current.getSpec());

            //update status and handle lifecycle
            //keep the user provided and move via lifecycle if needed
            DataItemBaseStatus status = DataItemBaseStatus.with(dto.getStatus());
            State nextState = status.getState() == null ? State.CREATED : State.valueOf(status.getState());

            //keep current state for update, we evaluate later
            status.setState(currentState.name());
            dto.setStatus(MapUtils.mergeMultipleMaps(dto.getStatus(), status.toMap()));
            if (log.isTraceEnabled()) {
                log.trace("storable dto: {}", dto);
            }

            if (currentState != nextState) {
                //move to next state
                log.debug("state change update from {} to {}, handle via lifecycle", currentState, nextState);

                //update via lifecycle transition
                dto = lifecycleManager.handle(dto, nextState);
            } else {
                //keep same state
                log.debug("same state update {}, handle via store", currentState);

                //direct update
                dto = entityService.update(id, dto);
            }

            return dto;
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.DATAITEM.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteDataItem(@NotNull String id, @Nullable Boolean cascade) {
        log.debug("delete dataItem with id {}", String.valueOf(id));
        try {
            DataItem dataItem = entityService.find(id);
            if (dataItem != null) {
                if (Boolean.TRUE.equals(cascade)) {
                    //files
                    log.debug("cascade delete files for dataItem with id {}", String.valueOf(id));

                    //extract path from spec
                    DataItemBaseSpec spec = new DataItemBaseSpec();
                    spec.configure(dataItem.getSpec());

                    String path = spec.getPath();
                    if (StringUtils.hasText(path)) {
                        //try to resolve credentials
                        UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
                        List<Credentials> credentials = auth != null && credentialsService != null
                            ? credentialsService.getCredentials(auth)
                            : null;

                        //delete files
                        filesService.remove(path, credentials);
                    }
                }

                //delete entity
                entityService.delete(id);
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteDataItems(@NotNull String project, @NotNull String name, @Nullable Boolean cascade) {
        log.debug("delete dataItems for project {} with name {}", project, name);
        if (Boolean.TRUE.equals(cascade)) {
            //delete one by one with cascade
            findDataItems(project, name).forEach(d -> deleteDataItem(d.getId(), Boolean.TRUE));
        } else {
            //bulk delete entities only
            Specification<DataItemEntity> spec = Specification.allOf(
                CommonSpecification.projectEquals(project),
                CommonSpecification.nameEquals(name)
            );
            try {
                long count = entityService.deleteAll(spec);
                log.debug("bulk deleted count {}", count);
            } catch (StoreException e) {
                log.error("store error: {}", e.getMessage());
                throw new SystemException(e.getMessage());
            }
        }
    }

    @Override
    public void deleteDataItemsByProject(@NotNull String project, @Nullable Boolean cascade) {
        log.debug("delete dataItems for project {}", project);
        try {
            if (Boolean.TRUE.equals(cascade)) {
                //delete one by one with cascade
                entityService
                    .searchAll(CommonSpecification.projectEquals(project))
                    .forEach(d -> deleteDataItem(d.getId(), Boolean.TRUE));
            } else {
                entityService.deleteAll(CommonSpecification.projectEquals(project));
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void indexOne(@NotNull String id) {
        if (indexer != null) {
            log.debug("index dataItem with id {}", String.valueOf(id));
            try {
                DataItem dataItem = entityService.get(id);
                indexer.index(entityBuilder.convert(dataItem));
            } catch (StoreException e) {
                log.error("store error: {}", e.getMessage());
                throw new SystemException(e.getMessage());
            }
        }
    }

    @Override
    public void reindexAll() {
        if (indexer != null) {
            log.debug("reindex all dataItems");

            //clear index
            indexer.clearIndex();

            //use pagination and batch
            boolean hasMore = true;
            int pageNumber = 0;
            while (hasMore) {
                hasMore = false;

                try {
                    Page<DataItem> page = entityService.list(PageRequest.of(pageNumber, EntityIndexer.PAGE_MAX_SIZE));
                    indexer.indexAll(
                        page.getContent().stream().map(e -> entityBuilder.convert(e)).collect(Collectors.toList())
                    );
                    hasMore = page.hasNext();
                } catch (IllegalArgumentException | StoreException | SystemException e) {
                    hasMore = false;

                    log.error("error with indexing: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public DownloadInfo downloadFileAsUrl(@NotNull String id) throws NoSuchEntityException, SystemException {
        log.debug("download url for dataitem with id {}", String.valueOf(id));

        try {
            DataItem entity = entityService.get(id);

            //extract path from spec
            DataItemBaseSpec spec = new DataItemBaseSpec();
            spec.configure(entity.getSpec());

            String path = spec.getPath();
            if (!StringUtils.hasText(path)) {
                throw new NoSuchEntityException("file");
            }

            //try to resolve credentials
            UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
            List<Credentials> credentials = auth != null && credentialsService != null
                ? credentialsService.getCredentials(auth)
                : null;

            DownloadInfo info = filesService.getDownloadAsUrl(path, credentials);
            if (log.isTraceEnabled()) {
                log.trace("download url for entity with id {}: {} -> {}", id, path, info);
            }

            return info;
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.DATAITEM.getValue());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public DownloadInfo downloadFileAsUrl(@NotNull String id, @NotNull String sub)
        throws NoSuchEntityException, SystemException {
        log.debug("download url for dataitem file with id {} and path {}", String.valueOf(id), String.valueOf(sub));

        try {
            DataItem dataItem = entityService.get(id);

            //extract path from spec
            DataItemBaseSpec spec = new DataItemBaseSpec();
            spec.configure(dataItem.getSpec());

            String path = spec.getPath();
            if (!StringUtils.hasText(path)) {
                throw new NoSuchEntityException("file");
            }

            String fullPath = Optional
                .ofNullable(sub)
                .map(s -> {
                    //build sub path *only* if not matching spec path
                    return path.endsWith(sub) ? path : path + sub;
                })
                .orElse(path);

            //try to resolve credentials
            UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
            List<Credentials> credentials = auth != null && credentialsService != null
                ? credentialsService.getCredentials(auth)
                : null;

            DownloadInfo info = filesService.getDownloadAsUrl(fullPath, credentials);
            if (log.isTraceEnabled()) {
                log.trace("download url for dataitem with id {} and path {}: {} -> {}", id, sub, path, info);
            }

            return info;
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.DATAITEM.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<FileInfo> getFileInfo(@NotNull String id) throws NoSuchEntityException, SystemException {
        log.debug("get files info for entity with id {}", String.valueOf(id));
        try {
            DataItem entity = entityService.get(id);
            StatusFieldAccessor statusFieldAccessor = StatusFieldAccessor.with(entity.getStatus());
            List<FileInfo> files = statusFieldAccessor.getFiles();

            //try to resolve credentials
            UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
            List<Credentials> credentials = auth != null && credentialsService != null
                ? credentialsService.getCredentials(auth)
                : null;

            if (files == null || files.isEmpty()) {
                FilesInfo filesInfo = filesInfoService.getFilesInfo(EntityName.DATAITEM.getValue(), id);
                if (filesInfo != null && (filesInfo.getFiles() != null)) {
                    files = filesInfo.getFiles();
                } else {
                    files = null;
                }
            }

            if (files == null) {
                //extract path from spec
                DataItemBaseSpec spec = new DataItemBaseSpec();
                spec.configure(entity.getSpec());

                String path = spec.getPath();
                if (!StringUtils.hasText(path)) {
                    throw new NoSuchEntityException("file");
                }

                files = filesService.getFileInfo(path, credentials);
            }

            if (files == null) {
                files = Collections.emptyList();
            }

            if (log.isTraceEnabled()) {
                log.trace("files info for entity with id {}: {} -> {}", id, EntityName.DATAITEM.getValue(), files);
            }

            return files;
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.DATAITEM.getValue());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void storeFileInfo(@NotNull String id, List<FileInfo> files) throws SystemException {
        try {
            DataItem entity = entityService.get(id);
            if (files != null) {
                log.debug("store files info for {}", entity.getId());
                filesInfoService.saveFilesInfo(EntityName.DATAITEM.getValue(), id, files);
            }
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.DATAITEM.getValue());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public UploadInfo uploadFileAsUrl(@NotNull String project, @Nullable String id, @NotNull String filename)
        throws NoSuchEntityException, SystemException {
        log.debug("upload url for dataItem with id {}: {}", String.valueOf(id), filename);

        try {
            String path =
                filesService.getDefaultStore(projectService.find(project)) +
                "/" +
                project +
                "/" +
                EntityName.DATAITEM.getValue() +
                "/" +
                id +
                (filename.startsWith("/") ? filename : "/" + filename);

            //dataItem may not exists (yet)
            DataItem dataItem = entityService.find(id);

            if (dataItem != null) {
                //extract path from spec
                DataItemBaseSpec spec = new DataItemBaseSpec();
                spec.configure(dataItem.getSpec());

                path = spec.getPath();
                if (!StringUtils.hasText(path)) {
                    throw new NoSuchEntityException("file");
                }
            }

            //try to resolve credentials
            UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
            List<Credentials> credentials = auth != null && credentialsService != null
                ? credentialsService.getCredentials(auth)
                : null;

            UploadInfo info = filesService.getUploadAsUrl(path, credentials);
            if (log.isTraceEnabled()) {
                log.trace("upload url for dataItem with id {}: {}", id, info);
            }

            return info;
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public UploadInfo startMultiPartUpload(@NotNull String project, @Nullable String id, @NotNull String filename)
        throws NoSuchEntityException, SystemException {
        log.debug("start upload url for dataItem with id {}: {}", String.valueOf(id), filename);

        try {
            String path =
                filesService.getDefaultStore(projectService.find(project)) +
                "/" +
                project +
                "/" +
                EntityName.DATAITEM.getValue() +
                "/" +
                id +
                "/" +
                (filename.startsWith("/") ? filename : "/" + filename);

            //dataItem may not exists (yet)
            DataItem dataItem = entityService.find(id);

            if (dataItem != null) {
                //extract path from spec
                DataItemBaseSpec spec = new DataItemBaseSpec();
                spec.configure(dataItem.getSpec());

                path = spec.getPath();
                if (!StringUtils.hasText(path)) {
                    throw new NoSuchEntityException("file");
                }
            }

            //try to resolve credentials
            UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
            List<Credentials> credentials = auth != null && credentialsService != null
                ? credentialsService.getCredentials(auth)
                : null;

            UploadInfo info = filesService.startMultiPartUpload(path, credentials);
            if (log.isTraceEnabled()) {
                log.trace("start upload url for dataItem with id {}: {}", id, info);
            }

            return info;
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public UploadInfo uploadMultiPart(
        @NotNull String project,
        @Nullable String id,
        @NotNull String filename,
        @NotNull String uploadId,
        @NotNull Integer partNumber
    ) throws NoSuchEntityException, SystemException {
        log.debug("upload part url for dataItem {}: {}", String.valueOf(id), filename);
        try {
            String path =
                filesService.getDefaultStore(projectService.find(project)) +
                "/" +
                project +
                "/" +
                EntityName.DATAITEM.getValue() +
                "/" +
                id +
                "/" +
                (filename.startsWith("/") ? filename : "/" + filename);

            //dataItem may not exists (yet)
            DataItem dataItem = entityService.find(id);

            if (dataItem != null) {
                //extract path from spec
                DataItemBaseSpec spec = new DataItemBaseSpec();
                spec.configure(dataItem.getSpec());

                path = spec.getPath();
                if (!StringUtils.hasText(path)) {
                    throw new NoSuchEntityException("file");
                }
            }

            //try to resolve credentials
            UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
            List<Credentials> credentials = auth != null && credentialsService != null
                ? credentialsService.getCredentials(auth)
                : null;

            UploadInfo info = filesService.uploadMultiPart(path, uploadId, partNumber, credentials);
            if (log.isTraceEnabled()) {
                log.trace("part upload url for dataItem with path {}: {}", path, info);
            }

            return info;
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public UploadInfo completeMultiPartUpload(
        @NotNull String project,
        @Nullable String id,
        @NotNull String filename,
        @NotNull String uploadId,
        @NotNull List<String> eTagPartList
    ) throws NoSuchEntityException, SystemException {
        log.debug("complete upload url for dataItem {}: {}", String.valueOf(id), filename);
        try {
            String path =
                filesService.getDefaultStore(projectService.find(project)) +
                "/" +
                project +
                "/" +
                EntityName.DATAITEM.getValue() +
                "/" +
                id +
                "/" +
                (filename.startsWith("/") ? filename : "/" + filename);

            //dataItem may not exists (yet)
            DataItem dataItem = entityService.find(id);

            if (dataItem != null) {
                //extract path from spec
                DataItemBaseSpec spec = new DataItemBaseSpec();
                spec.configure(dataItem.getSpec());

                path = spec.getPath();
                if (!StringUtils.hasText(path)) {
                    throw new NoSuchEntityException("file");
                }
            }

            //try to resolve credentials
            UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
            List<Credentials> credentials = auth != null && credentialsService != null
                ? credentialsService.getCredentials(auth)
                : null;

            UploadInfo info = filesService.completeMultiPartUpload(path, uploadId, eTagPartList, credentials);
            if (log.isTraceEnabled()) {
                log.trace("complete upload url for dataItem with path {}: {}", path, info);
            }

            return info;
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<RelationshipDetail> getRelationships(String id) {
        log.debug("get relationships for dataitem {}", String.valueOf(id));

        try {
            DataItem dataitem = entityService.get(id);
            return relationshipsManager.getRelationships(entityBuilder.convert(dataitem));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }
}
