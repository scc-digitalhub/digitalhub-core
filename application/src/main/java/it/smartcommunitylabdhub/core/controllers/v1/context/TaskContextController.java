package it.smartcommunitylabdhub.core.controllers.v1.context;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.models.entities.task.Task;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.services.entities.RunService;
import it.smartcommunitylabdhub.core.ApplicationKeys;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.models.entities.task.TaskEntity;
import it.smartcommunitylabdhub.core.models.queries.filters.entities.TaskEntityFilter;
import it.smartcommunitylabdhub.core.models.queries.services.SearchableTaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @Operation(
        summary = "Create a task in a project context",
        description = "create the task for the project (context)"
    )
    @PostMapping(
        value = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Task createTask(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @Valid @NotNull @RequestBody Task dto
    ) throws DuplicatedEntityException {
        //enforce project match
        dto.setProject(project);

        //create as new, will check for duplicated
        return taskService.createTask(dto);
    }

    @Operation(
        summary = "Retrieve all tasks for the project, with optional filter",
        description = "return a list of all tasks related to a project"
    )
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<Task> getTasksByProject(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @RequestParam(required = false) @Valid @Nullable TaskEntityFilter filter,
        @PageableDefault(page = 0, size = ApplicationKeys.DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "created", direction = Direction.DESC) }
        ) Pageable pageable
    ) {
        SearchFilter<TaskEntity> sf = null;
        if (filter != null) {
            sf = filter.toSearchFilter();
        }

        return taskService.searchTasksByProject(project, pageable, sf);
    }

    @Operation(
        summary = "Retrieve a specific task given the task id",
        description = "return a specific task identified by the id"
    )
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

    @Operation(
        summary = "Update if exist a task in a project context",
        description = "First check if project exist, if task exist update."
    )
    @PutMapping(
        value = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Task updateTask(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull Task taskDTO
    ) throws NoSuchEntityException {
        Task task = taskService.getTask(id);

        //check for project match
        if (!task.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return taskService.updateTask(id, taskDTO);
    }

    @Operation(
        summary = "Delete a specific task, with optional cascade",
        description = "First check if project exist, then delete a specific task"
    )
    @DeleteMapping(path = "/{id}")
    public void deleteTask(
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

    @Operation(
        summary = "List runs for a given task",
        description = "Return a list of runs defined for a specific task"
    )
    @GetMapping(path = "/{id}/runs", produces = "application/json; charset=UTF-8")
    public List<Run> getRuns(
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
}
