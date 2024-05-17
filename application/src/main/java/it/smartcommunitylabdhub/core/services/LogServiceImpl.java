package it.smartcommunitylabdhub.core.services;

import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.entities.log.Log;
import it.smartcommunitylabdhub.commons.models.entities.log.LogBaseSpec;
import it.smartcommunitylabdhub.commons.models.entities.project.Project;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.core.models.entities.AbstractEntity_;
import it.smartcommunitylabdhub.core.models.entities.LogEntity;
import it.smartcommunitylabdhub.core.models.entities.ProjectEntity;
import it.smartcommunitylabdhub.core.models.entities.RunEntity;
import it.smartcommunitylabdhub.core.models.queries.services.SearchableLogService;
import it.smartcommunitylabdhub.core.models.queries.specifications.CommonSpecification;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
public class LogServiceImpl implements SearchableLogService {

    @Autowired
    private EntityService<Log, LogEntity> entityService;

    @Autowired
    private EntityService<Run, RunEntity> runEntityService;

    @Autowired
    private EntityService<Project, ProjectEntity> projectService;

    @Override
    public Page<Log> listLogs(Pageable pageable) {
        log.debug("list logs page {}", pageable);
        try {
            return entityService.list(pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Log> searchLogs(Pageable pageable, @Nullable SearchFilter<LogEntity> filter) {
        log.debug("list logs page {}, filter {}", pageable, String.valueOf(filter));
        try {
            Specification<LogEntity> specification = filter != null ? filter.toSpecification() : null;
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
    public List<Log> listLogsByUser(@NotNull String user) {
        log.debug("list all logs for user {}  ", user);
        try {
            return entityService.searchAll(CommonSpecification.createdByEquals(user));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Log> listLogsByProject(@NotNull String project) {
        log.debug("list all logs for project {}  ", project);
        try {
            return entityService.searchAll(CommonSpecification.projectEquals(project));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Log> listLogsByProject(@NotNull String project, Pageable pageable) {
        log.debug("list logs for project {} page {}", project, pageable);
        Specification<LogEntity> specification = Specification.allOf(CommonSpecification.projectEquals(project));
        try {
            return entityService.search(specification, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Log> searchLogsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<LogEntity> filter
    ) {
        log.debug("list logs for project {} with {} page {}", project, String.valueOf(filter), pageable);
        Specification<LogEntity> filterSpecification = filter != null ? filter.toSpecification() : null;
        Specification<LogEntity> specification = Specification.allOf(
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
    public List<Log> getLogsByRunId(@NotNull String runId) {
        log.debug("list logs for run {}", runId);
        try {
            Run run = runEntityService.find(runId);
            if (run == null) {
                return Collections.emptyList();
            }

            //define a spec for logs building run path
            Specification<LogEntity> where = Specification.allOf(
                CommonSpecification.projectEquals(run.getProject()),
                createRunSpecification(runId)
            );

            //fetch all logs ordered by date ASC
            Specification<LogEntity> specification = (root, query, builder) -> {
                query.orderBy(builder.asc(root.get(AbstractEntity_.CREATED)));
                return where.toPredicate(root, query, builder);
            };

            return entityService.searchAll(specification);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Log findLog(@NotNull String id) {
        log.debug("find log with id {}", String.valueOf(id));
        try {
            return entityService.find(id);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Log getLog(@NotNull String id) throws NoSuchEntityException {
        log.debug("get log with id {}", String.valueOf(id));

        try {
            return entityService.get(id);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.LOG.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Log createLog(@NotNull Log dto) throws DuplicatedEntityException, BindException, IllegalArgumentException {
        log.debug("create log");
        try {
            //validate project
            String projectId = dto.getProject();
            if (!StringUtils.hasText(projectId) || projectService.find(projectId) == null) {
                throw new IllegalArgumentException("invalid or missing project");
            }

            try {
                //parse base spec to resolve run
                LogBaseSpec spec = new LogBaseSpec();
                spec.configure(dto.getSpec());

                String runId = spec.getRun();
                if (!StringUtils.hasText(runId)) {
                    throw new IllegalArgumentException("missing or invalid run");
                }

                Run run = runEntityService.find(runId);
                if (run == null) {
                    throw new IllegalArgumentException("missing or invalid run");
                }

                if (!projectId.equals(run.getProject())) {
                    throw new IllegalArgumentException("project mismatch");
                }

                //DISABLED, no need to evaluate spec for now
                // // Parse and export Spec
                // Spec spec = specRegistry.createSpec(dto.getKind(), dto.getSpec());
                // if (spec == null) {
                //     throw new IllegalArgumentException("invalid kind");
                // }

                // //validate
                // validator.validateSpec(spec);

                //create as new
                return entityService.create(dto);
            } catch (DuplicatedEntityException e) {
                throw new DuplicatedEntityException(EntityName.LOG.toString(), dto.getId());
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Log updateLog(@NotNull String id, @NotNull Log dto)
        throws NoSuchEntityException, BindException, IllegalArgumentException {
        log.debug("update log with id {}", String.valueOf(id));
        try {
            //fetch current and merge
            Log current = entityService.get(id);

            //hardcoded: run ref is not modifiable
            Map<String, Serializable> specMap = new HashMap<>();
            if (dto.getSpec() != null) {
                specMap.putAll(dto.getSpec());
            }
            if (current.getSpec() != null) {
                specMap.put("run", current.getSpec().get("run"));
            }

            //update spec
            dto.setSpec(specMap);

            //full update, log is modifiable
            return entityService.update(id, dto);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.LOG.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteLog(@NotNull String id) {
        log.debug("delete log with id {}", String.valueOf(id));
        try {
            Log log = findLog(id);
            if (log != null) {
                //delete the log
                entityService.delete(id);
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteLogsByRunId(@NotNull String runId) {
        log.debug("delete logs for run {}", runId);

        getLogsByRunId(runId).forEach(log -> deleteLog(log.getId()));
    }

    @Override
    public void deleteLogsByProject(@NotNull String project) {
        log.debug("delete logs for project {}", project);
        try {
            entityService.deleteAll(CommonSpecification.projectEquals(project));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    private Specification<LogEntity> createRunSpecification(String run) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.equal(root.get("run"), run);
        };
    }
}
