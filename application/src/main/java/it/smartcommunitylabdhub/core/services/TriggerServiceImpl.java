package it.smartcommunitylabdhub.core.services;

import it.smartcommunitylabdhub.commons.Fields;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.models.trigger.Trigger;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerBaseSpec;
import it.smartcommunitylabdhub.commons.services.SpecRegistry;
import it.smartcommunitylabdhub.core.components.infrastructure.specs.SpecValidator;
import it.smartcommunitylabdhub.core.components.run.TriggerLifecycleManager;
import it.smartcommunitylabdhub.core.models.entities.AbstractEntity_;
import it.smartcommunitylabdhub.core.models.entities.ProjectEntity;
import it.smartcommunitylabdhub.core.models.entities.TaskEntity;
import it.smartcommunitylabdhub.core.models.entities.TriggerEntity;
import it.smartcommunitylabdhub.core.models.events.EntityAction;
import it.smartcommunitylabdhub.core.models.events.EntityOperation;
import it.smartcommunitylabdhub.core.models.queries.services.SearchableTriggerService;
import it.smartcommunitylabdhub.core.models.queries.specifications.CommonSpecification;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
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
public class TriggerServiceImpl implements SearchableTriggerService {

    @Autowired
    private EntityService<Trigger, TriggerEntity> entityService;

    @Autowired
    private EntityService<Task, TaskEntity> taskService;

    @Autowired
    private EntityService<Project, ProjectEntity> projectService;

    @Autowired
    private SpecRegistry specRegistry;

    @Autowired
    private SpecValidator validator;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    TriggerLifecycleManager triggerManager;

    @Override
    public Page<Trigger> listTriggers(Pageable pageable) {
        log.debug("list triggers page {}", pageable);
        try {
            return entityService.list(pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Trigger> listTriggersByUser(@NotNull String user) {
        log.debug("list all triggers for user {}  ", user);
        try {
            return entityService.searchAll(CommonSpecification.createdByEquals(user));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Trigger> listTriggersByProject(@NotNull String project) {
        log.debug("list all triggers for project {}  ", project);
        try {
            return entityService.searchAll(CommonSpecification.projectEquals(project));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Trigger> listTriggersByProject(@NotNull String project, Pageable pageable) {
        log.debug("list triggers for project {} page {}", project, pageable);
        Specification<TriggerEntity> specification = Specification.allOf(CommonSpecification.projectEquals(project));
        try {
            return entityService.search(specification, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Trigger> listTriggersByTaskId(@NotNull String taskId) {
        log.debug("list triggers for taskId {}", taskId);
        try {
            Task task = taskService.find(taskId);
            if (task == null) {
                return Collections.emptyList();
            }

            //define a spec for triggers building task path
            String path = (task.getKind() + "://" + task.getProject() + "/" + task.getId());

            Specification<TriggerEntity> where = Specification.allOf(
                CommonSpecification.projectEquals(task.getProject()),
                createTaskSpecification(path)
            );

            //fetch all triggers ordered by kind ASC
            Specification<TriggerEntity> specification = (root, query, builder) -> {
                query.orderBy(builder.asc(root.get(AbstractEntity_.KIND)));
                return where.toPredicate(root, query, builder);
            };

            return entityService.searchAll(specification);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Trigger> searchTriggers(Pageable pageable, @Nullable SearchFilter<TriggerEntity> filter) {
        log.debug("list triggers page {}, filter {}", pageable, String.valueOf(filter));
        try {
            Specification<TriggerEntity> specification = filter != null ? filter.toSpecification() : null;
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
    public Page<Trigger> searchTriggersByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<TriggerEntity> filter
    ) {
        log.debug("list triggers for project {} with {} page {}", project, String.valueOf(filter), pageable);
        Specification<TriggerEntity> filterSpecification = filter != null ? filter.toSpecification() : null;
        Specification<TriggerEntity> specification = Specification.allOf(
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
    public Trigger findTrigger(@NotNull String id) {
        log.debug("find trigger with id {}", String.valueOf(id));
        try {
            return entityService.find(id);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Trigger getTrigger(@NotNull String id) throws NoSuchEntityException {
        log.debug("get trigger with id {}", String.valueOf(id));

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
    public Trigger createTrigger(@NotNull Trigger dto)
        throws DuplicatedEntityException, BindException, IllegalArgumentException {
        log.debug("create trigger");
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

                //check task is valid
                TriggerBaseSpec baseSpec = TriggerBaseSpec.from(dto.getSpec());
                if (!StringUtils.hasText(baseSpec.getTask())) {
                    throw new IllegalArgumentException("spec: missing task");
                }
                if (!StringUtils.hasText(baseSpec.getFunction())) {
                    throw new IllegalArgumentException("spec: missing function");
                }

                //create as new
                Trigger trigger = entityService.create(dto);

                trigger = triggerManager.run(trigger);

                return trigger;
            } catch (DuplicatedEntityException e) {
                throw new DuplicatedEntityException(EntityName.TRIGGER.toString(), dto.getId());
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Trigger updateTrigger(@NotNull String id, @NotNull Trigger dto)
        throws NoSuchEntityException, BindException, IllegalArgumentException {
        log.debug("update trigger with id {}", String.valueOf(id));
        try {
            //fetch current and merge
            Trigger current = entityService.get(id);

            //spec is not modificable: enforce current
            dto.setSpec(current.getSpec());

            Spec spec = specRegistry.createSpec(dto.getKind(), dto.getSpec());
            if (spec == null) {
                throw new IllegalArgumentException("invalid kind");
            }

            //validate
            validator.validateSpec(spec);

            //update spec as exported
            dto.setSpec(spec.toMap());

            //check task and function are valid
            TriggerBaseSpec baseSpec = TriggerBaseSpec.from(dto.getSpec());
            if (!StringUtils.hasText(baseSpec.getTask())) {
                throw new IllegalArgumentException("spec: missing task");
            }
            if (!StringUtils.hasText(baseSpec.getFunction())) {
                throw new IllegalArgumentException("spec: missing function");
            }

            //access task details from ref, same as run
            RunSpecAccessor specAccessor = RunSpecAccessor.with(dto.getSpec());

            //check project match
            if (dto.getProject() != null && !dto.getProject().equals(specAccessor.getProject())) {
                throw new IllegalArgumentException("project mismatch");
            }

            //full update, trigger is modifiable
            return entityService.update(id, dto);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.TASK.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteTrigger(@NotNull String id, @Nullable Boolean cascade) {
        log.debug("delete trigger with id {}", String.valueOf(id));
        try {
            Trigger trigger = findTrigger(id);
            if (trigger != null) {
                //delete job
                triggerManager.stop(trigger);

                if (Boolean.TRUE.equals(cascade)) {
                    log.debug("cascade delete for trigger with id {}", String.valueOf(id));

                    //delete via async event to let manager do cleanups
                    log.debug("publish op: delete for {}", trigger.getId());
                    EntityOperation<Trigger> event = new EntityOperation<>(trigger, EntityAction.DELETE);
                    if (log.isTraceEnabled()) {
                        log.trace("event: {}", String.valueOf(event));
                    }

                    eventPublisher.publishEvent(event);
                }

                //delete the trigger
                entityService.delete(id);
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    private Specification<TriggerEntity> createTaskSpecification(String task) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.equal(root.get(Fields.TASK), task);
        };
    }
}
