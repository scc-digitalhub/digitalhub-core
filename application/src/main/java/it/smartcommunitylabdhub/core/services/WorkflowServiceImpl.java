package it.smartcommunitylabdhub.core.services;

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

import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.base.RelationshipDetail;
import it.smartcommunitylabdhub.commons.models.entities.project.Project;
import it.smartcommunitylabdhub.commons.models.entities.workflow.Workflow;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.services.SpecRegistry;
import it.smartcommunitylabdhub.commons.services.entities.TaskService;
import it.smartcommunitylabdhub.core.components.infrastructure.specs.SpecValidator;
import it.smartcommunitylabdhub.core.models.builders.workflow.WorkflowEntityBuilder;
import it.smartcommunitylabdhub.core.models.entities.AbstractEntity_;
import it.smartcommunitylabdhub.core.models.entities.ProjectEntity;
import it.smartcommunitylabdhub.core.models.entities.WorkflowEntity;
import it.smartcommunitylabdhub.core.models.indexers.BaseEntityIndexer;
import it.smartcommunitylabdhub.core.models.indexers.IndexableEntityService;
import it.smartcommunitylabdhub.core.models.indexers.WorkflowEntityIndexer;
import it.smartcommunitylabdhub.core.models.queries.services.SearchableWorkflowService;
import it.smartcommunitylabdhub.core.models.queries.specifications.CommonSpecification;
import it.smartcommunitylabdhub.core.models.relationships.RelationshipsWorkflowService;
import it.smartcommunitylabdhub.core.models.relationships.WorkflowEntityRelationshipsManager;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class WorkflowServiceImpl implements SearchableWorkflowService, IndexableEntityService<WorkflowEntity>, RelationshipsWorkflowService {

    @Autowired
    private EntityService<Workflow, WorkflowEntity> entityService;

    @Autowired
    private EntityService<Project, ProjectEntity> projectService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private WorkflowEntityIndexer indexer;

    @Autowired
    private WorkflowEntityBuilder entityBuilder;

    @Autowired
    private SpecRegistry specRegistry;

    @Autowired
    private SpecValidator validator;
    
    @Autowired
    private WorkflowEntityRelationshipsManager relationshipsManager;

    @Override
    public Page<Workflow> listWorkflows(Pageable pageable) {
        log.debug("list workflows page {}", pageable);
        try {
            return entityService.list(pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Workflow> listLatestWorkflows() {
        log.debug("list latest workflows");
        Specification<WorkflowEntity> specification = CommonSpecification.latest();

        try {
            return entityService.searchAll(specification);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Workflow> listLatestWorkflows(Pageable pageable) {
        log.debug("list latest workflows page {}", pageable);
        Specification<WorkflowEntity> specification = CommonSpecification.latest();
        try {
            return entityService.search(specification, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Workflow> listWorkflowsByUser(@NotNull String user) {
        log.debug("list all workflows for user {}  ", user);
        try {
            return entityService.searchAll(CommonSpecification.createdByEquals(user));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Workflow> searchWorkflows(Pageable pageable, @Nullable SearchFilter<WorkflowEntity> filter) {
        log.debug("search workflows page {}, filter {}", pageable, String.valueOf(filter));
        try {
            Specification<WorkflowEntity> specification = filter != null ? filter.toSpecification() : null;
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
    public Page<Workflow> searchLatestWorkflows(Pageable pageable, @Nullable SearchFilter<WorkflowEntity> filter) {
        log.debug("search latest workflows with {} page {}", String.valueOf(filter), pageable);
        Specification<WorkflowEntity> filterSpecification = filter != null ? filter.toSpecification() : null;
        Specification<WorkflowEntity> specification = Specification.allOf(
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
    public List<Workflow> listWorkflowsByProject(@NotNull String project) {
        log.debug("list all workflows for project {}", project);
        Specification<WorkflowEntity> specification = Specification.allOf(CommonSpecification.projectEquals(project));
        try {
            return entityService.searchAll(specification);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Workflow> listWorkflowsByProject(@NotNull String project, Pageable pageable) {
        log.debug("list all workflows for project {}  page {}", project, pageable);
        Specification<WorkflowEntity> specification = Specification.allOf(CommonSpecification.projectEquals(project));
        try {
            return entityService.search(specification, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Workflow> listLatestWorkflowsByProject(@NotNull String project) {
        log.debug("list latest workflows for project {}", project);
        Specification<WorkflowEntity> specification = Specification.allOf(
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
    public Page<Workflow> listLatestWorkflowsByProject(@NotNull String project, Pageable pageable) {
        log.debug("list latest workflows for project {}  page {}", project, pageable);
        Specification<WorkflowEntity> specification = Specification.allOf(
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
    public Page<Workflow> searchWorkflowsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<WorkflowEntity> filter
    ) {
        log.debug("search all workflows for project {} with {} page {}", project, String.valueOf(filter), pageable);
        Specification<WorkflowEntity> filterSpecification = filter != null ? filter.toSpecification() : null;
        Specification<WorkflowEntity> specification = Specification.allOf(
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
    public Page<Workflow> searchLatestWorkflowsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<WorkflowEntity> filter
    ) {
        log.debug("search latest workflows for project {} with {} page {}", project, String.valueOf(filter), pageable);
        Specification<WorkflowEntity> filterSpecification = filter != null ? filter.toSpecification() : null;
        Specification<WorkflowEntity> specification = Specification.allOf(
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
    public List<Workflow> findWorkflows(@NotNull String project, @NotNull String name) {
        log.debug("find workflows for project {} with name {}", project, name);

        //fetch all versions ordered by date DESC
        Specification<WorkflowEntity> where = Specification.allOf(
            CommonSpecification.projectEquals(project),
            CommonSpecification.nameEquals(name)
        );
        Specification<WorkflowEntity> specification = (root, query, builder) -> {
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
    public Page<Workflow> findWorkflows(@NotNull String project, @NotNull String name, Pageable pageable) {
        log.debug("find workflows for project {} with name {} page {}", project, name, pageable);

        //fetch all versions ordered by date DESC
        Specification<WorkflowEntity> where = Specification.allOf(
            CommonSpecification.projectEquals(project),
            CommonSpecification.nameEquals(name)
        );
        Specification<WorkflowEntity> specification = (root, query, builder) -> {
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
    public Workflow findWorkflow(@NotNull String id) {
        log.debug("find workflow with id {}", String.valueOf(id));
        try {
            return entityService.find(id);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Workflow getWorkflow(@NotNull String id) throws NoSuchEntityException {
        log.debug("get workflow with id {}", String.valueOf(id));

        try {
            return entityService.get(id);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.ARTIFACT.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Workflow getLatestWorkflow(@NotNull String project, @NotNull String name) throws NoSuchEntityException {
        log.debug("get latest workflow for project {} with name {}", project, name);
        try {
            //fetch latest version ordered by date DESC
            Specification<WorkflowEntity> specification = CommonSpecification.latestByProject(project, name);
            return entityService.searchAll(specification).stream().findFirst().orElseThrow(NoSuchEntityException::new);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Workflow createWorkflow(@NotNull Workflow dto)
        throws DuplicatedEntityException, BindException, IllegalArgumentException {
        log.debug("create workflow");
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
                throw new DuplicatedEntityException(EntityName.WORKFLOW.toString(), dto.getId());
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Workflow updateWorkflow(@NotNull String id, @NotNull Workflow workflowDTO)
        throws NoSuchEntityException, BindException, IllegalArgumentException {
        log.debug("update workflow with id {}", String.valueOf(id));
        try {
            //fetch current and merge
            Workflow current = entityService.get(id);

            //spec is not modificable: enforce current
            workflowDTO.setSpec(current.getSpec());

            //update
            return entityService.update(id, workflowDTO);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.WORKFLOW.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }


    @Override
    public Workflow updateWorkflow(@NotNull String id, @NotNull Workflow workflowDTO, boolean force)
        throws NoSuchEntityException {
        log.debug("force update workflow with id {}", String.valueOf(id));
        try {
            //force update
            //no validation
            return entityService.update(id, workflowDTO);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.WORKFLOW.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteWorkflow(@NotNull String id, @Nullable Boolean cascade) {
        log.debug("delete workflow with id {}", String.valueOf(id));
        try {
            Workflow workflow = findWorkflow(id);
            if (workflow != null) {
                if (Boolean.TRUE.equals(cascade)) {
                    //tasks
                    log.debug("cascade delete tasks for function with id {}", String.valueOf(id));
                    taskService.deleteTasksByFunctionId(id, EntityName.WORKFLOW);
                }

                //delete the function
                entityService.delete(id);
            }
            // delete the workflow
            entityService.delete(id);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteWorkflows(@NotNull String project, @NotNull String name) {
        log.debug("delete workflows for project {} with name {}", project, name);

        //delete with cascade
        findWorkflows(project, name).forEach(w -> deleteWorkflow(w.getId(), Boolean.TRUE));
    }

    @Override
    public void deleteWorkflowsByProject(@NotNull String project) {
        log.debug("delete workflows for project {}", project);
        try {
            entityService
                .searchAll(CommonSpecification.projectEquals(project))
                .forEach(w -> deleteWorkflow(w.getId(), Boolean.TRUE));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void indexOne(@NotNull String id) {
        log.debug("index workflow with id {}", String.valueOf(id));
        try {
            Workflow workflow = entityService.get(id);
            indexer.index(entityBuilder.convert(workflow));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void reindexAll() {
        log.debug("reindex all workflows");

        //clear index
        indexer.clearIndex();

        //use pagination and batch
        boolean hasMore = true;
        int pageNumber = 0;
        while (hasMore) {
            hasMore = false;

            try {
                Page<Workflow> page = entityService.list(PageRequest.of(pageNumber, BaseEntityIndexer.PAGE_MAX_SIZE));
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
	public List<RelationshipDetail> getRelationships(String entityId) {
		return relationshipsManager.getRelationships(entityId);
	}
}
