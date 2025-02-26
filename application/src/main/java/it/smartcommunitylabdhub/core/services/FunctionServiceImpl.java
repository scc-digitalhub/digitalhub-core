package it.smartcommunitylabdhub.core.services;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;

import it.smartcommunitylabdhub.commons.Fields;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.services.RelationshipsAwareEntityService;
import it.smartcommunitylabdhub.commons.services.SpecRegistry;
import it.smartcommunitylabdhub.commons.services.TaskService;
import it.smartcommunitylabdhub.core.components.infrastructure.specs.SpecValidator;
import it.smartcommunitylabdhub.core.models.builders.function.FunctionEntityBuilder;
import it.smartcommunitylabdhub.core.models.entities.AbstractEntity_;
import it.smartcommunitylabdhub.core.models.entities.FunctionEntity;
import it.smartcommunitylabdhub.core.models.entities.ProjectEntity;
import it.smartcommunitylabdhub.core.models.entities.TaskEntity;
import it.smartcommunitylabdhub.core.models.indexers.EntityIndexer;
import it.smartcommunitylabdhub.core.models.indexers.IndexableEntityService;
import it.smartcommunitylabdhub.core.models.queries.services.SearchableFunctionService;
import it.smartcommunitylabdhub.core.models.queries.specifications.CommonSpecification;
import it.smartcommunitylabdhub.core.relationships.FunctionEntityRelationshipsManager;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class FunctionServiceImpl
    implements
        SearchableFunctionService, IndexableEntityService<FunctionEntity>, RelationshipsAwareEntityService<Function> {

    @Autowired
    private EntityService<Function, FunctionEntity> entityService;

    @Autowired
    private EntityService<Project, ProjectEntity> projectService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private EntityService<Task, TaskEntity> taskEntityService;

    @Autowired
    private EntityIndexer<FunctionEntity> indexer;

    @Autowired
    private FunctionEntityBuilder entityBuilder;

    @Autowired
    private SpecRegistry specRegistry;

    @Autowired
    private SpecValidator validator;

    @Autowired
    private FunctionEntityRelationshipsManager relationshipsManager;

    @Override
    public Page<Function> listFunctions(Pageable pageable) {
        log.debug("list functions page {}", pageable);
        try {
            return entityService.list(pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Function> listLatestFunctions() {
        log.debug("list latest functions");
        Specification<FunctionEntity> specification = CommonSpecification.latest();

        try {
            return entityService.searchAll(specification);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Function> listLatestFunctions(Pageable pageable) {
        log.debug("list latest functions page {}", pageable);
        Specification<FunctionEntity> specification = CommonSpecification.latest();
        try {
            return entityService.search(specification, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Function> listFunctionsByUser(@NotNull String user) {
        log.debug("list all functions for user {}  ", user);
        try {
            return entityService.searchAll(CommonSpecification.createdByEquals(user));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Function> searchFunctions(Pageable pageable, @Nullable SearchFilter<FunctionEntity> filter) {
        log.debug("list functions page {}, filter {}", pageable, String.valueOf(filter));
        try {
            Specification<FunctionEntity> specification = filter != null ? filter.toSpecification() : null;
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
    public Page<Function> searchLatestFunctions(Pageable pageable, @Nullable SearchFilter<FunctionEntity> filter) {
        log.debug("search latest functions with {} page {}", String.valueOf(filter), pageable);
        Specification<FunctionEntity> filterSpecification = filter != null ? filter.toSpecification() : null;
        Specification<FunctionEntity> specification = Specification.allOf(
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
    public List<Function> listFunctionsByProject(@NotNull String project) {
        log.debug("list functions for project {}", project);
        Specification<FunctionEntity> specification = Specification.allOf(CommonSpecification.projectEquals(project));
        try {
            return entityService.searchAll(specification);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Function> listFunctionsByProject(@NotNull String project, Pageable pageable) {
        log.debug("list functions for project {} page {}", project, pageable);
        Specification<FunctionEntity> specification = Specification.allOf(CommonSpecification.projectEquals(project));
        try {
            return entityService.search(specification, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Function> listLatestFunctionsByProject(@NotNull String project) {
        log.debug("list latest functions for project {}", project);
        Specification<FunctionEntity> specification = Specification.allOf(
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
    public Page<Function> listLatestFunctionsByProject(@NotNull String project, Pageable pageable) {
        log.debug("list latest functions for project {} page {}", project, pageable);
        Specification<FunctionEntity> specification = Specification.allOf(
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
    public Page<Function> searchLatestFunctionsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<FunctionEntity> filter
    ) {
        log.debug("search latest functions for project {} with {} page {}", project, String.valueOf(filter), pageable);
        Specification<FunctionEntity> filterSpecification = filter != null ? filter.toSpecification() : null;
        Specification<FunctionEntity> specification = Specification.allOf(
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
    public Page<Function> searchFunctionsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<FunctionEntity> filter
    ) {
        log.debug("search functions for project {} with {} page {}", project, String.valueOf(filter), pageable);
        Specification<FunctionEntity> filterSpecification = filter != null ? filter.toSpecification() : null;
        Specification<FunctionEntity> specification = Specification.allOf(
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
    public List<Function> findFunctions(@NotNull String project, @NotNull String name) {
        log.debug("find functions for project {} with name {}", project, name);

        //fetch all versions ordered by date DESC
        Specification<FunctionEntity> where = Specification.allOf(
            CommonSpecification.projectEquals(project),
            CommonSpecification.nameEquals(name)
        );
        Specification<FunctionEntity> specification = (root, query, builder) -> {
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
    public Page<Function> findFunctions(@NotNull String project, @NotNull String name, Pageable pageable) {
        log.debug("find functions for project {} with name {} page {}", project, name, pageable);

        //fetch all versions ordered by date DESC
        Specification<FunctionEntity> where = Specification.allOf(
            CommonSpecification.projectEquals(project),
            CommonSpecification.nameEquals(name)
        );
        Specification<FunctionEntity> specification = (root, query, builder) -> {
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
    public Function findFunction(@NotNull String id) {
        log.debug("find function with id {}", String.valueOf(id));
        try {
            return entityService.find(id);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Function getFunction(@NotNull String id) throws NoSuchEntityException {
        log.debug("get function with id {}", String.valueOf(id));

        try {
            return entityService.get(id);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.FUNCTION.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Function getLatestFunction(@NotNull String project, @NotNull String name) throws NoSuchEntityException {
        log.debug("get latest function for project {} with name {}", project, name);
        try {
            //fetch latest version ordered by date DESC
            Specification<FunctionEntity> specification = CommonSpecification.latestByProject(project, name);
            return entityService.searchAll(specification).stream().findFirst().orElseThrow(NoSuchEntityException::new);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Function createFunction(@NotNull Function dto)
        throws DuplicatedEntityException, BindException, IllegalArgumentException {
        log.debug("create function");
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
                throw new DuplicatedEntityException(EntityName.FUNCTION.toString(), dto.getId());
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Function updateFunction(@NotNull String id, @NotNull Function functionDTO)
        throws NoSuchEntityException, BindException, IllegalArgumentException {
        log.debug("update function with id {}", String.valueOf(id));
        try {
            //fetch current and merge
            Function current = entityService.get(id);

            //spec is not modificable: enforce current
            functionDTO.setSpec(current.getSpec());

            //update
            return entityService.update(id, functionDTO);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.FUNCTION.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Function updateFunction(@NotNull String id, @NotNull Function functionDTO, boolean force)
        throws NoSuchEntityException {
        log.debug("force update function with id {}", String.valueOf(id));
        try {
            //force update
            //no validation
            return entityService.update(id, functionDTO);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.FUNCTION.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteFunction(@NotNull String id, @Nullable Boolean cascade) {
        log.debug("delete function with id {}", String.valueOf(id));

        Function function = findFunction(id);
        if (function != null) {
            if (Boolean.TRUE.equals(cascade)) {
                //tasks
                log.debug("cascade delete tasks for function with id {}", String.valueOf(id));
                getTasksByFunctionId(id).forEach(task -> taskService.deleteTask(task.getId(), Boolean.TRUE));
            }

            try {
                //delete the function
                entityService.delete(id);
            } catch (StoreException e) {
                log.error("store error: {}", e.getMessage());
                throw new SystemException(e.getMessage());
            }
        }
    }

    @Override
    public void deleteFunctions(@NotNull String project, @NotNull String name) {
        log.debug("delete functions for project {} with name {}", project, name);

        //delete with cascade
        findFunctions(project, name).forEach(function -> deleteFunction(function.getId(), Boolean.TRUE));
    }

    @Override
    public void deleteFunctionsByProject(@NotNull String project) {
        log.debug("delete functions for project {}", project);
        try {
            entityService
                .searchAll(CommonSpecification.projectEquals(project))
                .forEach(f -> deleteFunction(f.getId(), Boolean.TRUE));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void indexOne(@NotNull String id) {
        log.debug("index function with id {}", String.valueOf(id));
        try {
            Function function = entityService.get(id);
            indexer.index(entityBuilder.convert(function));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void reindexAll() {
        log.debug("reindex all functions");

        //clear index
        indexer.clearIndex();

        //use pagination and batch
        boolean hasMore = true;
        int pageNumber = 0;
        while (hasMore) {
            hasMore = false;

            try {
                Page<Function> page = entityService.list(PageRequest.of(pageNumber, EntityIndexer.PAGE_MAX_SIZE));
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

    @Override
    public List<RelationshipDetail> getRelationships(String id) {
        log.debug("get relationships for function {}", String.valueOf(id));

        try {
            Function function = entityService.get(id);
            return relationshipsManager.getRelationships(entityBuilder.convert(function));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Task> getTasksByFunctionId(@NotNull String functionId) {
        log.debug("list tasks for function {}", functionId);
        try {
            Function function = entityService.find(functionId);
            if (function == null) {
                return Collections.emptyList();
            }

            //define a spec for tasks building function path
            String path =
                (function.getKind() +
                    "://" +
                    function.getProject() +
                    "/" +
                    function.getName() +
                    ":" +
                    function.getId());

            Specification<TaskEntity> where = Specification.allOf(
                CommonSpecification.projectEquals(function.getProject()),
                createFunctionSpecification(path)
            );

            //fetch all tasks ordered by kind ASC
            Specification<TaskEntity> specification = (root, query, builder) -> {
                query.orderBy(builder.asc(root.get(AbstractEntity_.KIND)));
                return where.toPredicate(root, query, builder);
            };

            return taskEntityService.searchAll(specification);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    private Specification<TaskEntity> createFunctionSpecification(String function) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.equal(root.get(Fields.FUNCTION), function);
        };
    }
}
