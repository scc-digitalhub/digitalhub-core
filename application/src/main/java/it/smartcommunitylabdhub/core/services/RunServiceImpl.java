package it.smartcommunitylabdhub.core.services;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;

import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.base.Executable;
import it.smartcommunitylabdhub.commons.models.base.RelationshipDetail;
import it.smartcommunitylabdhub.commons.models.entities.project.Project;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.models.entities.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.entities.task.Task;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.models.utils.RunUtils;
import it.smartcommunitylabdhub.commons.models.utils.TaskUtils;
import it.smartcommunitylabdhub.commons.services.LogService;
import it.smartcommunitylabdhub.commons.services.SpecRegistry;
import it.smartcommunitylabdhub.core.components.infrastructure.specs.SpecValidator;
import it.smartcommunitylabdhub.core.models.entities.AbstractEntity_;
import it.smartcommunitylabdhub.core.models.entities.ProjectEntity;
import it.smartcommunitylabdhub.core.models.entities.RunEntity;
import it.smartcommunitylabdhub.core.models.entities.TaskEntity;
import it.smartcommunitylabdhub.core.models.queries.services.SearchableRunService;
import it.smartcommunitylabdhub.core.models.queries.specifications.CommonSpecification;
import it.smartcommunitylabdhub.core.models.relationships.RelationshipsRunService;
import it.smartcommunitylabdhub.core.models.relationships.RunEntityRelationshipsManager;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class RunServiceImpl implements SearchableRunService, RelationshipsRunService {

    @Autowired
    private EntityService<Run, RunEntity> entityService;

    @Autowired
    private EntityService<Task, TaskEntity> taskEntityService;

    @Autowired
    private LogService logService;

    @Autowired
    private ExecutableEntityService executableEntityServiceProvider;

    @Autowired
    private EntityService<Project, ProjectEntity> projectService;

    @Autowired
    private SpecRegistry specRegistry;

    @Autowired
    private SpecValidator validator;
    
    @Autowired
    private RunEntityRelationshipsManager relationshipsManager;

    @Override
    public Page<Run> listRuns(Pageable pageable) {
        log.debug("list runs page {}", pageable);
        try {
            return entityService.list(pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Run> listRunsByUser(@NotNull String user) {
        log.debug("list all runs for user {}  ", user);
        try {
            return entityService.searchAll(CommonSpecification.createdByEquals(user));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Run> listRunsByProject(@NotNull String project) {
        log.debug("list all runs for project {}  ", project);
        try {
            return entityService.searchAll(CommonSpecification.projectEquals(project));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Run> searchRuns(Pageable pageable, @Nullable SearchFilter<RunEntity> filter) {
        log.debug("list runs page {}, filter {}", pageable, String.valueOf(filter));
        try {
            Specification<RunEntity> specification = filter != null ? filter.toSpecification() : null;
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
    public Page<Run> listRunsByProject(@NotNull String project, Pageable pageable) {
        log.debug("list runs for project {} page {}", project, pageable);
        Specification<RunEntity> specification = Specification.allOf(CommonSpecification.projectEquals(project));
        try {
            return entityService.search(specification, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Run> searchRunsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<RunEntity> filter
    ) {
        log.debug("list runs for project {} with {} page {}", project, String.valueOf(filter), pageable);
        Specification<RunEntity> filterSpecification = filter != null ? filter.toSpecification() : null;
        Specification<RunEntity> specification = Specification.allOf(
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
    public List<Run> getRunsByTaskId(@NotNull String taskId) {
        log.debug("list runs for task {}", taskId);
        try {
            Task task = taskEntityService.find(taskId);
            if (task == null) {
                return Collections.emptyList();
            }

            //define a spec for runs building task path
            Specification<RunEntity> where = Specification.allOf(
                CommonSpecification.projectEquals(task.getProject()),
                createTaskSpecification(RunUtils.buildTaskString(task))
            );

            //fetch all runs ordered by created DESC
            Specification<RunEntity> specification = (root, query, builder) -> {
                query.orderBy(builder.desc(root.get(AbstractEntity_.CREATED)));
                return where.toPredicate(root, query, builder);
            };

            return entityService.searchAll(specification);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Run findRun(@NotNull String id) {
        log.debug("find run with id {}", String.valueOf(id));
        try {
            return entityService.find(id);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Run getRun(@NotNull String id) throws NoSuchEntityException {
        log.debug("get run with id {}", String.valueOf(id));

        try {
            return entityService.get(id);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.RUN.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Run createRun(@NotNull Run dto) throws DuplicatedEntityException, BindException, IllegalArgumentException {
        log.debug("create run");
        if (log.isTraceEnabled()) {
            log.trace("dto: {}", dto);
        }
        try {
            //validate project
            String projectId = dto.getProject();
            if (!StringUtils.hasText(projectId) || projectService.find(projectId) == null) {
                throw new IllegalArgumentException("invalid or missing project");
            }
            //TODO check if project exists?

            //check base run spec
            RunBaseSpec runSpec = new RunBaseSpec();
            runSpec.configure(dto.getSpec());

            // Parse and export Spec
            Spec spec = specRegistry.createSpec(dto.getKind(), dto.getSpec());
            if (spec == null) {
                throw new IllegalArgumentException("invalid kind");
            }

            //validate
            validator.validateSpec(spec);

            //update spec as exported
            dto.setSpec(spec.toMap());

            String taskPath = runSpec.getTask();
            if (!StringUtils.hasText(taskPath)) {
                throw new IllegalArgumentException("missing task");
            }

            RunSpecAccessor runSpecAccessor = RunUtils.parseTask(taskPath);
            if (!StringUtils.hasText(runSpecAccessor.getProject())) {
                throw new IllegalArgumentException("spec: missing project");
            }

            //check project match
            if (!projectId.equals(runSpecAccessor.getProject())) {
                throw new IllegalArgumentException("project mismatch");
            }
            if (!StringUtils.hasText(runSpecAccessor.getTask())) {
                throw new IllegalArgumentException("spec: missing task");
            }
            if (!StringUtils.hasText(runSpecAccessor.getFunction())) {
                throw new IllegalArgumentException("spec: missing function");
            }
            if (!StringUtils.hasText(runSpecAccessor.getVersion())) {
                throw new IllegalArgumentException("spec: missing version");
            }
            String functionId = runSpecAccessor.getVersion();

            //check if function exists and matches
            Executable executable = executableEntityServiceProvider
                .getEntityServiceByRuntime(runSpecAccessor.getRuntime())
                .find(functionId);
            if (executable == null) {
                throw new IllegalArgumentException("invalid function");
            }
            if (!projectId.equals(executable.getProject())) {
                throw new IllegalArgumentException("project mismatch");
            }
            if (!executable.getName().equals(runSpecAccessor.getFunction())) {
                throw new IllegalArgumentException("function name mismatch");
            }

            // retrieve task by looking up value
            // define a spec for matching task
            Specification<TaskEntity> where = Specification.allOf(
                CommonSpecification.projectEquals(executable.getProject()),
                createFunctionSpecification(TaskUtils.buildString(executable)),
                createTaskKindSpecification(runSpecAccessor.getTask())
            );

            Task task = taskEntityService.searchAll(where).stream().findFirst().orElse(null);
            if (task == null) {
                throw new IllegalArgumentException("invalid task");
            }

            try {
                // store the run in db
                return entityService.create(dto);
            } catch (DuplicatedEntityException e) {
                throw new DuplicatedEntityException(EntityName.RUN.toString(), dto.getId());
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Run updateRun(@NotNull String id, @NotNull Run dto)
        throws NoSuchEntityException, BindException, IllegalArgumentException {
        log.debug("update run with id {}", String.valueOf(id));
        try {
            //fetch current and merge
            Run current = entityService.get(id);

            //spec is not modifiable unless in specific states
            //TODO: implement logic to disable spec update in some states
            // runDTO.setSpec(current.getSpec());

            //TODO: implement logic to update status only in some states

            // Parse and export Spec
            Spec spec = specRegistry.createSpec(dto.getKind(), dto.getSpec());
            if (spec == null) {
                throw new IllegalArgumentException("invalid kind");
            }

            //validate
            validator.validateSpec(spec);

            //update spec as exported
            dto.setSpec(spec.toMap());

            //full update, run is modifiable
            return entityService.update(id, dto);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.RUN.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteRun(@NotNull String id, @Nullable Boolean cascade) {
        log.debug("delete run with id {}", String.valueOf(id));
        try {
            Run run = findRun(id);
            if (run != null) {
                if (Boolean.TRUE.equals(cascade)) {
                    log.debug("cascade delete logs for run with id {}", String.valueOf(id));
                    logService.deleteLogsByRunId(id);
                }

                //delete the run
                entityService.delete(id);
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteRunsByTaskId(@NotNull String taskId) {
        log.debug("delete runs for task {}", taskId);

        getRunsByTaskId(taskId).forEach(run -> deleteRun(run.getId(), Boolean.TRUE));
    }

    private Specification<RunEntity> createTaskSpecification(String task) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("task"), task);
    }

    private Specification<TaskEntity> createFunctionSpecification(String function) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("function"), function);
    }

    private Specification<TaskEntity> createTaskKindSpecification(String kind) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("kind"), kind);
    }

	@Override
	public List<RelationshipDetail> getRelationships(String project, String entityId) {
		return relationshipsManager.getRelationships(project, entityId);
	}
}
