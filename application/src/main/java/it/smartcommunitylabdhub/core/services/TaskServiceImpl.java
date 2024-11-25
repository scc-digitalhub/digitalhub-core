package it.smartcommunitylabdhub.core.services;

import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.base.Executable;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.models.workflow.Workflow;
import it.smartcommunitylabdhub.commons.services.RunService;
import it.smartcommunitylabdhub.commons.services.SpecRegistry;
import it.smartcommunitylabdhub.core.components.infrastructure.specs.SpecValidator;
import it.smartcommunitylabdhub.core.models.entities.FunctionEntity;
import it.smartcommunitylabdhub.core.models.entities.ProjectEntity;
import it.smartcommunitylabdhub.core.models.entities.TaskEntity;
import it.smartcommunitylabdhub.core.models.entities.WorkflowEntity;
import it.smartcommunitylabdhub.core.models.events.EntityAction;
import it.smartcommunitylabdhub.core.models.events.EntityOperation;
import it.smartcommunitylabdhub.core.models.queries.services.SearchableTaskService;
import it.smartcommunitylabdhub.core.models.queries.specifications.CommonSpecification;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;

@Service
@Transactional
@Slf4j
public class TaskServiceImpl implements SearchableTaskService {

    @Autowired
    private EntityService<Task, TaskEntity> entityService;

    @Autowired
    private EntityService<Function, FunctionEntity> functionService;

    @Autowired
    private EntityService<Workflow, WorkflowEntity> workflowService;

    @Autowired
    private EntityService<Project, ProjectEntity> projectService;

    @Autowired
    private RunService runService;

    @Autowired
    private SpecRegistry specRegistry;

    @Autowired
    private SpecValidator validator;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public Page<Task> listTasks(Pageable pageable) {
        log.debug("list tasks page {}", pageable);
        try {
            return entityService.list(pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Task> listTasksByUser(@NotNull String user) {
        log.debug("list all tasks for user {}  ", user);
        try {
            return entityService.searchAll(CommonSpecification.createdByEquals(user));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Task> listTasksByProject(@NotNull String project) {
        log.debug("list all tasks for project {}  ", project);
        try {
            return entityService.searchAll(CommonSpecification.projectEquals(project));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Task> searchTasks(Pageable pageable, @Nullable SearchFilter<TaskEntity> filter) {
        log.debug("list tasks page {}, filter {}", pageable, String.valueOf(filter));
        try {
            Specification<TaskEntity> specification = filter != null ? filter.toSpecification() : null;
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
    public Page<Task> listTasksByProject(@NotNull String project, Pageable pageable) {
        log.debug("list tasks for project {} page {}", project, pageable);
        Specification<TaskEntity> specification = Specification.allOf(CommonSpecification.projectEquals(project));
        try {
            return entityService.search(specification, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Task> searchTasksByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<TaskEntity> filter
    ) {
        log.debug("list tasks for project {} with {} page {}", project, String.valueOf(filter), pageable);
        Specification<TaskEntity> filterSpecification = filter != null ? filter.toSpecification() : null;
        Specification<TaskEntity> specification = Specification.allOf(
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
    public Task findTask(@NotNull String id) {
        log.debug("find task with id {}", String.valueOf(id));
        try {
            return entityService.find(id);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Task getTask(@NotNull String id) throws NoSuchEntityException {
        log.debug("get task with id {}", String.valueOf(id));

        try {
            return entityService.get(id);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.TASK.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Task createTask(@NotNull Task dto)
        throws DuplicatedEntityException, BindException, IllegalArgumentException {
        log.debug("create task");
        try {
            //validate project
            String projectId = dto.getProject();
            if (!StringUtils.hasText(projectId) || projectService.find(projectId) == null) {
                throw new IllegalArgumentException("invalid or missing project");
            }

            try {
                // Parse and export Spec
                Spec spec = specRegistry.createSpec(dto.getKind(), dto.getSpec());
                if (spec == null) {
                    throw new IllegalArgumentException("invalid kind");
                }

                //validate
                validator.validateSpec(spec);

                //update spec as exported
                dto.setSpec(spec.toMap());

                //check if the same task already exists for the function
                TaskSpecAccessor taskSpecAccessor = TaskSpecAccessor.with(dto.getSpec());
                if (!StringUtils.hasText(taskSpecAccessor.getProject())) {
                    throw new IllegalArgumentException("spec: missing project");
                }
                if (!StringUtils.hasText(taskSpecAccessor.getRuntime())) {
                    throw new IllegalArgumentException("missing runtime");
                }

                //check project match
                if (dto.getProject() != null && !dto.getProject().equals(taskSpecAccessor.getProject())) {
                    throw new IllegalArgumentException("project mismatch");
                }
                dto.setProject(taskSpecAccessor.getProject());

                // task may belong to function or to workflow
                Executable executable = null;
                String function = taskSpecAccessor.getFunction();
                String workflow = taskSpecAccessor.getWorkflow();

                if (StringUtils.hasText(function)) {
                    String functionId = taskSpecAccessor.getFunctionId();
                    executable = functionService.find(functionId);
                }
                if (StringUtils.hasText(workflow)) {
                    String workflowId = taskSpecAccessor.getWorkflowId();
                    executable = workflowService.find(workflowId);
                }

                if (executable == null) {
                    throw new IllegalArgumentException("invalid executable entity");
                }

                //create as new
                return entityService.create(dto);
            } catch (DuplicatedEntityException e) {
                throw new DuplicatedEntityException(EntityName.TASK.toString(), dto.getId());
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Task updateTask(@NotNull String id, @NotNull Task dto)
        throws NoSuchEntityException, BindException, IllegalArgumentException {
        log.debug("update task with id {}", String.valueOf(id));
        try {
            //fetch current and merge
            Task current = entityService.get(id);

            //hardcoded: function ref is not modifiable
            Map<String, Serializable> specMap = new HashMap<>();
            if (dto.getSpec() != null) {
                specMap.putAll(dto.getSpec());
            }
            if (current.getSpec() != null) {
                specMap.put("function", current.getSpec().get("function"));
            }

            Spec spec = specRegistry.createSpec(dto.getKind(), specMap);
            if (spec == null) {
                throw new IllegalArgumentException("invalid kind");
            }

            //validate
            validator.validateSpec(spec);

            //update spec as exported
            dto.setSpec(spec.toMap());

            //full update, task is modifiable
            return entityService.update(id, dto);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.TASK.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteTask(@NotNull String id, @Nullable Boolean cascade) {
        log.debug("delete task with id {}", String.valueOf(id));
        try {
            Task task = findTask(id);
            if (task != null) {
                if (Boolean.TRUE.equals(cascade)) {
                    log.debug("cascade delete runs for task with id {}", String.valueOf(id));

                    //delete via async event to let manager do cleanups
                    runService
                        .getRunsByTaskId(id)
                        .forEach(run -> {
                            log.debug("publish op: delete for {}", run.getId());
                            EntityOperation<Run> event = new EntityOperation<>(run, EntityAction.DELETE);
                            if (log.isTraceEnabled()) {
                                log.trace("event: {}", String.valueOf(event));
                            }

                            eventPublisher.publishEvent(event);
                        });
                }

                //delete the task
                entityService.delete(id);
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }
}
