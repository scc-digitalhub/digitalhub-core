package it.smartcommunitylabdhub.core.services;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.files.FileInfo;
import it.smartcommunitylabdhub.commons.models.files.FilesInfo;
import it.smartcommunitylabdhub.commons.models.metrics.Metrics;
import it.smartcommunitylabdhub.commons.models.metrics.NumberOrNumberArray;
import it.smartcommunitylabdhub.commons.models.model.Model;
import it.smartcommunitylabdhub.commons.models.model.ModelBaseSpec;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.services.FilesInfoService;
import it.smartcommunitylabdhub.commons.services.MetricsService;
import it.smartcommunitylabdhub.commons.services.RelationshipsAwareEntityService;
import it.smartcommunitylabdhub.commons.services.SpecRegistry;
import it.smartcommunitylabdhub.core.components.infrastructure.specs.SpecValidator;
import it.smartcommunitylabdhub.core.components.security.UserAuthenticationHelper;
import it.smartcommunitylabdhub.core.metrics.MetricsManager;
import it.smartcommunitylabdhub.core.models.builders.model.ModelEntityBuilder;
import it.smartcommunitylabdhub.core.models.entities.AbstractEntity_;
import it.smartcommunitylabdhub.core.models.entities.ModelEntity;
import it.smartcommunitylabdhub.core.models.entities.ProjectEntity;
import it.smartcommunitylabdhub.core.models.indexers.EntityIndexer;
import it.smartcommunitylabdhub.core.models.indexers.IndexableEntityService;
import it.smartcommunitylabdhub.core.models.queries.services.SearchableModelService;
import it.smartcommunitylabdhub.core.models.queries.specifications.CommonSpecification;
import it.smartcommunitylabdhub.core.relationships.ModelEntityRelationshipsManager;
import it.smartcommunitylabdhub.files.models.DownloadInfo;
import it.smartcommunitylabdhub.files.models.UploadInfo;
import it.smartcommunitylabdhub.files.service.EntityFilesService;
import it.smartcommunitylabdhub.files.service.FilesService;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
public class ModelServiceImpl
    implements
        SearchableModelService,
        IndexableEntityService<ModelEntity>,
        EntityFilesService<Model>,
        RelationshipsAwareEntityService<Model>,
        MetricsService<Model> {

    @Autowired
    private EntityService<Model, ModelEntity> entityService;

    @Autowired
    private EntityService<Project, ProjectEntity> projectService;

    @Autowired(required = false)
    private EntityIndexer<ModelEntity> indexer;

    @Autowired
    private ModelEntityBuilder entityBuilder;

    @Autowired
    SpecRegistry specRegistry;

    @Autowired
    private SpecValidator validator;

    @Autowired
    private FilesService filesService;

    @Autowired
    private FilesInfoService filesInfoService;

    @Autowired
    private ModelEntityRelationshipsManager relationshipsManager;

    @Autowired
    private MetricsManager metricsManager;

    @Override
    public Page<Model> listModels(Pageable pageable) {
        log.debug("list models page {}", pageable);
        try {
            return entityService.list(pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Model> listLatestModels() {
        log.debug("list latest models");
        Specification<ModelEntity> specification = CommonSpecification.latest();

        try {
            return entityService.searchAll(specification);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Model> listLatestModels(Pageable pageable) {
        log.debug("list latest models page {}", pageable);
        Specification<ModelEntity> specification = CommonSpecification.latest();
        try {
            return entityService.search(specification, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Model> listModelsByUser(@NotNull String user) {
        log.debug("list all models for user {}", user);
        try {
            return entityService.searchAll(CommonSpecification.createdByEquals(user));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Model> searchModels(Pageable pageable, SearchFilter<ModelEntity> filter) {
        log.debug("list models page {}, filter {}", pageable, String.valueOf(filter));

        try {
            Specification<ModelEntity> specification = filter != null ? filter.toSpecification() : null;
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
    public Page<Model> searchLatestModels(Pageable pageable, @Nullable SearchFilter<ModelEntity> filter) {
        log.debug("search latest models with {} page {}", String.valueOf(filter), pageable);
        Specification<ModelEntity> filterSpecification = filter != null ? filter.toSpecification() : null;
        Specification<ModelEntity> specification = Specification.allOf(
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
    public List<Model> listModelsByProject(@NotNull String project) {
        log.debug("list all models for project {}", project);
        Specification<ModelEntity> specification = Specification.allOf(CommonSpecification.projectEquals(project));
        try {
            return entityService.searchAll(specification);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Model> listModelsByProject(@NotNull String project, Pageable pageable) {
        log.debug("list all models for project {} page {}", project, pageable);
        Specification<ModelEntity> specification = Specification.allOf(CommonSpecification.projectEquals(project));
        try {
            return entityService.search(specification, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Model> listLatestModelsByProject(@NotNull String project) {
        log.debug("list latest models for project {}", project);
        Specification<ModelEntity> specification = Specification.allOf(
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
    public Page<Model> listLatestModelsByProject(@NotNull String project, Pageable pageable) {
        log.debug("list latest models for project {} page {}", project, pageable);
        Specification<ModelEntity> specification = Specification.allOf(
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
    public Page<Model> searchModelsByProject(
        @NotNull String project,
        Pageable pageable,
        SearchFilter<ModelEntity> filter
    ) {
        log.debug("search all models for project {} with {} page {}", project, String.valueOf(filter), pageable);
        Specification<ModelEntity> filterSpecification = filter != null ? filter.toSpecification() : null;
        Specification<ModelEntity> specification = Specification.allOf(
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
    public Page<Model> searchLatestModelsByProject(
        @NotNull String project,
        Pageable pageable,
        SearchFilter<ModelEntity> filter
    ) {
        log.debug("search latest models for project {} with {} page {}", project, String.valueOf(filter), pageable);
        Specification<ModelEntity> filterSpecification = filter != null ? filter.toSpecification() : null;
        Specification<ModelEntity> specification = Specification.allOf(
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
    public List<Model> findModels(@NotNull String project, @NotNull String name) {
        log.debug("find models for project {} with name {}", project, name);

        //fetch all versions ordered by date DESC
        Specification<ModelEntity> where = Specification.allOf(
            CommonSpecification.projectEquals(project),
            CommonSpecification.nameEquals(name)
        );

        Specification<ModelEntity> specification = (root, query, builder) -> {
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
    public Page<Model> findModels(@NotNull String project, @NotNull String name, Pageable pageable) {
        log.debug("find models for project {} with name {} page {}", project, name, pageable);

        //fetch all versions ordered by date DESC
        Specification<ModelEntity> where = Specification.allOf(
            CommonSpecification.projectEquals(project),
            CommonSpecification.nameEquals(name)
        );
        Specification<ModelEntity> specification = (root, query, builder) -> {
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
    public Model findModel(@NotNull String id) {
        log.debug("find model with id {}", String.valueOf(id));
        try {
            return entityService.find(id);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Model getModel(@NotNull String id) throws NoSuchEntityException {
        log.debug("get model with id {}", String.valueOf(id));

        try {
            return entityService.get(id);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.MODEL.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Model getLatestModel(@NotNull String project, @NotNull String name) throws NoSuchEntityException {
        log.debug("get latest model for project {} with name {}", project, name);
        try {
            //fetch latest version ordered by date DESC
            Specification<ModelEntity> specification = CommonSpecification.latestByProject(project, name);
            return entityService.searchAll(specification).stream().findFirst().orElseThrow(NoSuchEntityException::new);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Model createModel(@NotNull Model dto)
        throws DuplicatedEntityException, BindException, IllegalArgumentException {
        log.debug("create model");
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

            try {
                if (log.isTraceEnabled()) {
                    log.trace("storable dto: {}", dto);
                }

                return entityService.create(dto);
            } catch (DuplicatedEntityException e) {
                throw new DuplicatedEntityException(EntityName.MODEL.toString(), dto.getId());
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Model updateModel(@NotNull String id, @NotNull Model modelDTO)
        throws NoSuchEntityException, BindException, IllegalArgumentException {
        log.debug("model model with id {}", String.valueOf(id));
        try {
            //fetch current and merge
            Model current = entityService.get(id);

            //spec is not modificable: enforce current
            modelDTO.setSpec(current.getSpec());

            //update
            return entityService.update(id, modelDTO);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.MODEL.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteModel(@NotNull String id) {
        log.debug("delete model with id {}", String.valueOf(id));
        try {
            entityService.delete(id);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteModels(@NotNull String project, @NotNull String name) {
        log.debug("delete models for project {} with name {}", project, name);

        Specification<ModelEntity> spec = Specification.allOf(
            CommonSpecification.projectEquals(project),
            CommonSpecification.nameEquals(name)
        );
        try {
            long count = entityService.deleteAll(spec);
            log.debug("deleted count {}", count);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteModelsByProject(@NotNull String project) {
        log.debug("delete models for project {}", project);
        try {
            entityService.deleteAll(CommonSpecification.projectEquals(project));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void indexOne(@NotNull String id) {
        if (indexer != null) {
            log.debug("index model with id {}", String.valueOf(id));
            try {
                Model model = entityService.get(id);
                indexer.index(entityBuilder.convert(model));
            } catch (StoreException e) {
                log.error("store error: {}", e.getMessage());
                throw new SystemException(e.getMessage());
            }
        }
    }

    @Override
    public void reindexAll() {
        if (indexer != null) {
            log.debug("reindex all models");

            //clear index
            indexer.clearIndex();

            //use pagination and batch
            boolean hasMore = true;
            int pageNumber = 0;
            while (hasMore) {
                hasMore = false;

                try {
                    Page<Model> page = entityService.list(PageRequest.of(pageNumber, EntityIndexer.PAGE_MAX_SIZE));
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
        log.debug("download url for model with id {}", String.valueOf(id));

        try {
            Model entity = entityService.get(id);

            //extract path from spec
            ModelBaseSpec spec = new ModelBaseSpec();
            spec.configure(entity.getSpec());

            String path = spec.getPath();
            if (!StringUtils.hasText(path)) {
                throw new NoSuchEntityException("file");
            }

            DownloadInfo info = filesService.getDownloadAsUrl(path, UserAuthenticationHelper.getUserAuthentication());
            if (log.isTraceEnabled()) {
                log.trace("download url for entity with id {}: {} -> {}", id, path, info);
            }

            return info;
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.MODEL.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public DownloadInfo downloadFileAsUrl(@NotNull String id, @NotNull String sub)
        throws NoSuchEntityException, SystemException {
        log.debug("download url for model file with id {} and path {}", String.valueOf(id), String.valueOf(sub));

        try {
            Model model = entityService.get(id);

            //extract path from spec
            ModelBaseSpec spec = new ModelBaseSpec();
            spec.configure(model.getSpec());

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

            DownloadInfo info = filesService.getDownloadAsUrl(fullPath, UserAuthenticationHelper.getUserAuthentication());
            if (log.isTraceEnabled()) {
                log.trace("download url for model with id {} and path {}: {} -> {}", id, sub, path, info);
            }

            return info;
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.MODEL.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<FileInfo> getFileInfo(@NotNull String id) throws NoSuchEntityException, SystemException {
        log.debug("get storage metadata for model with id {}", String.valueOf(id));
        try {
            Model entity = entityService.get(id);
            StatusFieldAccessor statusFieldAccessor = StatusFieldAccessor.with(entity.getStatus());
            List<FileInfo> files = statusFieldAccessor.getFiles();

            if (files == null || files.isEmpty()) {
                FilesInfo filesInfo = filesInfoService.getFilesInfo(EntityName.MODEL.getValue(), id);
                if (filesInfo != null && (filesInfo.getFiles() != null)) {
                    files = filesInfo.getFiles();
                } else {
                    files = null;
                }
            }

            if (files == null) {
                //extract path from spec
                ModelBaseSpec spec = new ModelBaseSpec();
                spec.configure(entity.getSpec());

                String path = spec.getPath();
                if (!StringUtils.hasText(path)) {
                    throw new NoSuchEntityException("file");
                }

                files = filesService.getFileInfo(path, UserAuthenticationHelper.getUserAuthentication());
            }

            if (files == null) {
                files = Collections.emptyList();
            }

            if (log.isTraceEnabled()) {
                log.trace("files info for entity with id {}: {} -> {}", id, EntityName.MODEL.getValue(), files);
            }

            return files;
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.MODEL.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void storeFileInfo(@NotNull String id, List<FileInfo> files) throws SystemException {
        try {
            Model entity = entityService.get(id);
            if (files != null) {
                log.debug("store files info for {}", entity.getId());
                filesInfoService.saveFilesInfo(EntityName.MODEL.getValue(), id, files);
            }
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.MODEL.getValue());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public UploadInfo uploadFileAsUrl(@Nullable String id, @NotNull String filename)
        throws NoSuchEntityException, SystemException {
        log.debug("upload url for model with id {}: {}", String.valueOf(id), filename);

        try {
            String path =
                filesService.getDefaultStore() +
                "/" +
                EntityName.MODEL.getValue() +
                "/" +
                id +
                (filename.startsWith("/") ? filename : "/" + filename);

            //model may not exists (yet)
            Model model = entityService.find(id);

            if (model != null) {
                //extract path from spec
                ModelBaseSpec spec = new ModelBaseSpec();
                spec.configure(model.getSpec());

                path = spec.getPath();
                if (!StringUtils.hasText(path)) {
                    throw new NoSuchEntityException("file");
                }
            }

            UploadInfo info = filesService.getUploadAsUrl(path, UserAuthenticationHelper.getUserAuthentication());
            if (log.isTraceEnabled()) {
                log.trace("upload url for model with id {}: {}", id, info);
            }

            return info;
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public UploadInfo startMultiPartUpload(@Nullable String id, @NotNull String filename)
        throws NoSuchEntityException, SystemException {
        log.debug("start upload url for model with id {}: {}", String.valueOf(id), filename);

        try {
            String path =
                filesService.getDefaultStore() +
                "/" +
                EntityName.MODEL.getValue() +
                "/" +
                id +
                "/" +
                (filename.startsWith("/") ? filename : "/" + filename);

            //model may not exists (yet)
            Model model = entityService.find(id);

            if (model != null) {
                //extract path from spec
                ModelBaseSpec spec = new ModelBaseSpec();
                spec.configure(model.getSpec());

                path = spec.getPath();
                if (!StringUtils.hasText(path)) {
                    throw new NoSuchEntityException("file");
                }
            }

            UploadInfo info = filesService.startMultiPartUpload(path, UserAuthenticationHelper.getUserAuthentication());
            if (log.isTraceEnabled()) {
                log.trace("start upload url for model with id {}: {}", id, info);
            }

            return info;
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public UploadInfo uploadMultiPart(
        @Nullable String id,
        @NotNull String filename,
        @NotNull String uploadId,
        @NotNull Integer partNumber
    ) throws NoSuchEntityException, SystemException {
        log.debug("upload part url for model {}: {}", String.valueOf(id), filename);
        try {
            String path =
                filesService.getDefaultStore() +
                "/" +
                EntityName.MODEL.getValue() +
                "/" +
                id +
                "/" +
                (filename.startsWith("/") ? filename : "/" + filename);

            //model may not exists (yet)
            Model model = entityService.find(id);

            if (model != null) {
                //extract path from spec
                ModelBaseSpec spec = new ModelBaseSpec();
                spec.configure(model.getSpec());

                path = spec.getPath();
                if (!StringUtils.hasText(path)) {
                    throw new NoSuchEntityException("file");
                }
            }

            UploadInfo info = filesService.uploadMultiPart(path, uploadId, partNumber, UserAuthenticationHelper.getUserAuthentication());
            if (log.isTraceEnabled()) {
                log.trace("part upload url for model with path {}: {}", path, info);
            }

            return info;
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public UploadInfo completeMultiPartUpload(
        @Nullable String id,
        @NotNull String filename,
        @NotNull String uploadId,
        @NotNull List<String> eTagPartList
    ) throws NoSuchEntityException, SystemException {
        log.debug("complete upload url for model {}: {}", String.valueOf(id), filename);
        try {
            String path =
                filesService.getDefaultStore() +
                "/" +
                EntityName.MODEL.getValue() +
                "/" +
                id +
                "/" +
                (filename.startsWith("/") ? filename : "/" + filename);

            //model may not exists (yet)
            Model model = entityService.find(id);

            if (model != null) {
                //extract path from spec
                ModelBaseSpec spec = new ModelBaseSpec();
                spec.configure(model.getSpec());

                path = spec.getPath();
                if (!StringUtils.hasText(path)) {
                    throw new NoSuchEntityException("file");
                }
            }

            UploadInfo info = filesService.completeMultiPartUpload(path, uploadId, eTagPartList, UserAuthenticationHelper.getUserAuthentication());
            if (log.isTraceEnabled()) {
                log.trace("complete upload url for model with path {}: {}", path, info);
            }

            return info;
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<RelationshipDetail> getRelationships(String id) {
        log.debug("get relationships for model {}", String.valueOf(id));

        try {
            Model model = entityService.get(id);
            return relationshipsManager.getRelationships(entityBuilder.convert(model));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Map<String, NumberOrNumberArray> getMetrics(@NotNull String entityId)
        throws StoreException, SystemException {
        try {
            Model entity = entityService.get(entityId);
            StatusFieldAccessor statusFieldAccessor = StatusFieldAccessor.with(entity.getStatus());
            Map<String, NumberOrNumberArray> metrics = statusFieldAccessor.getMetrics();
            if (metrics != null) {
                Map<String, NumberOrNumberArray> entityMetrics = metricsManager.getMetrics(
                    EntityName.MODEL.getValue(),
                    entityId
                );
                for (Map.Entry<String, NumberOrNumberArray> entry : entityMetrics.entrySet()) {
                    if (metrics.containsKey(entry.getKey())) continue;
                    metrics.put(entry.getKey(), entry.getValue());
                }
                return metrics;
            }
            return metricsManager.getMetrics(EntityName.MODEL.getValue(), entityId);
        } catch (Exception e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public NumberOrNumberArray getMetrics(@NotNull String entityId, @NotNull String name)
        throws StoreException, SystemException {
        try {
            Model entity = entityService.get(entityId);
            StatusFieldAccessor statusFieldAccessor = StatusFieldAccessor.with(entity.getStatus());
            Map<String, NumberOrNumberArray> metrics = statusFieldAccessor.getMetrics();
            if ((metrics != null) && metrics.containsKey(name)) return metrics.get(name);
            return metricsManager.getMetrics(EntityName.MODEL.getValue(), entityId, name);
        } catch (Exception e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Metrics saveMetrics(@NotNull String entityId, @NotNull String name, NumberOrNumberArray data)
        throws StoreException, SystemException {
        return metricsManager.saveMetrics(EntityName.MODEL.getValue(), entityId, name, data);
    }
}
