package it.smartcommunitylabdhub.core.controllers.v1.base;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.models.entities.workflow.Workflow;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.core.ApplicationKeys;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.models.entities.workflow.WorkflowEntity;
import it.smartcommunitylabdhub.core.models.queries.filters.entities.WorkflowEntityFilter;
import it.smartcommunitylabdhub.core.models.queries.services.SearchableWorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
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
@RequestMapping("/workflows")
//TODO evaluate permissions for project via lookup in dto
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Validated
@Slf4j
@Tag(name = "Workflow base API", description = "Endpoints related to workflows management")
public class WorkflowController {

    @Autowired
    SearchableWorkflowService workflowService;

    @Operation(summary = "Create workflow", description = "Create an workflow and return")
    @PostMapping(
        value = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Workflow createWorkflow(@RequestBody @Valid @NotNull Workflow dto) throws DuplicatedEntityException {
        return workflowService.createWorkflow(dto);
    }

    @Operation(summary = "List workflows", description = "Return a list of all workflows")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<Workflow> getWorkflows(
        @ParameterObject @RequestParam(required = false) @Valid @Nullable WorkflowEntityFilter filter,
        @ParameterObject @PageableDefault(page = 0, size = ApplicationKeys.DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "created", direction = Direction.DESC) }
        ) Pageable pageable
    ) {
        SearchFilter<WorkflowEntity> sf = null;
        if (filter != null) {
            sf = filter.toSearchFilter();
        }

        return workflowService.searchWorkflows(pageable, sf);
    }

    @Operation(summary = "Get an workflow by id", description = "Return an workflow")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public Workflow getWorkflow(@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id)
        throws NoSuchEntityException {
        return workflowService.getWorkflow(id);
    }

    @Operation(summary = "Update specific workflow", description = "Update and return the workflow")
    @PutMapping(
        path = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Workflow updateWorkflow(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull Workflow dto
    ) throws NoSuchEntityException {
        return workflowService.updateWorkflow(id, dto);
    }

    @Operation(summary = "Delete an workflow", description = "Delete a specific workflow")
    @DeleteMapping(path = "/{id}")
    public void deleteWorkflow(@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id) {
        workflowService.deleteWorkflow(id);
    }
}
