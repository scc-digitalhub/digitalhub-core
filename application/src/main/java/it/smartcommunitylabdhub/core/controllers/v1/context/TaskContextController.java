package it.smartcommunitylabdhub.core.controllers.v1.context;

import java.util.List;

import javax.annotation.Nullable;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.base.RelationshipDetail;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.models.entities.task.Task;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.services.entities.RunService;
import it.smartcommunitylabdhub.core.ApplicationKeys;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.models.entities.TaskEntity;
import it.smartcommunitylabdhub.core.models.queries.filters.entities.TaskEntityFilter;
import it.smartcommunitylabdhub.core.models.queries.services.SearchableTaskService;
import it.smartcommunitylabdhub.core.models.relationships.RelationshipsTaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;

@RestController
@ApiVersion("v1")
@RequestMapping("/-/{project}/tasks")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "Task context API", description = "Endpoints related to tasks management in Context")
public class TaskContextController {

    @Autowired
    SearchableTaskService taskService;

    @Autowired
    RunService runService;
    
    @Autowired
    RelationshipsTaskService relationshipsService;

    @Operation(summary = "Create a task in a project context")
    @PostMapping(
        value = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Task createTask(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @RequestBody @Valid @NotNull Task dto
    ) throws DuplicatedEntityException, IllegalArgumentException, SystemException, BindException {
        //enforce project match
        dto.setProject(project);

        //create as new, will check for duplicated
        return taskService.createTask(dto);
    }

    @Operation(summary = "Search tasks, with optional filter")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<Task> searchTasks(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @ParameterObject @Valid @Nullable TaskEntityFilter filter,
        @ParameterObject @PageableDefault(page = 0, size = ApplicationKeys.DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "created", direction = Direction.DESC) }
        ) Pageable pageable
    ) {
        SearchFilter<TaskEntity> sf = null;
        if (filter != null) {
            sf = filter.toSearchFilter();
        }

        return taskService.searchTasksByProject(project, pageable, sf);
    }

    @Operation(summary = "Retrieve a specific task given the task id")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public Task getTaskById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Task task = taskService.getTask(id);

        //check for project match
        if (!task.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return task;
    }

    @Operation(summary = "Update if exist a task in a project context")
    @PutMapping(
        value = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Task updateTaskById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull Task taskDTO
    ) throws NoSuchEntityException, IllegalArgumentException, SystemException, BindException {
        Task task = taskService.getTask(id);

        //check for project match
        if (!task.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return taskService.updateTask(id, taskDTO);
    }

    @Operation(summary = "Delete a specific task, with optional cascade")
    @DeleteMapping(path = "/{id}")
    public void deleteTaskById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam(required = false) Boolean cascade
    ) throws NoSuchEntityException {
        Task task = taskService.getTask(id);

        //check for project  match
        if (!task.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        taskService.deleteTask(id, cascade);
    }

    /*
     * Runs
     */

    @Operation(summary = "List runs for a given task")
    @GetMapping(path = "/{id}/runs", produces = "application/json; charset=UTF-8")
    public List<Run> getRunsByTaskId(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Task task = taskService.getTask(id);

        //check for project
        if (!task.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return runService.getRunsByTaskId(id);
    }
    
    @Operation(summary = "Get relationships info for a given entity, if available")
    @GetMapping(path = "/{id}/relationships", produces = "application/json; charset=UTF-8")
    public List<RelationshipDetail> getRelationshipsById(
    		@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
    		@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id) throws NoSuchEntityException {
    	Task entity = taskService.getTask(id);

        //check for project and name match
        if ((entity != null) && !entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }
        
        return relationshipsService.getRelationships(id);
    }
    
}
