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
import it.smartcommunitylabdhub.commons.models.entities.workflow.Workflow;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.core.ApplicationKeys;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.models.entities.WorkflowEntity;
import it.smartcommunitylabdhub.core.models.queries.filters.entities.WorkflowEntityFilter;
import it.smartcommunitylabdhub.core.models.queries.services.SearchableWorkflowService;
import it.smartcommunitylabdhub.core.models.relationships.RelationshipsWorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;

@RestController
@ApiVersion("v1")
@RequestMapping("/-/{project}/workflows")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "Workflow context API", description = "Endpoints related to workflows management for project")
public class WorkflowContextController {

    @Autowired
    SearchableWorkflowService workflowService;
    
    @Autowired
    RelationshipsWorkflowService relationshipsService;

    @Operation(
        summary = "Create a workflow in a project context",
        description = "create the workflow for the project (context)"
    )
    @PostMapping(
        value = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Workflow createWorkflow(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @Valid @NotNull @RequestBody Workflow dto
    ) throws DuplicatedEntityException, IllegalArgumentException, SystemException, BindException {
        //enforce project match
        dto.setProject(project);

        //create as new
        return workflowService.createWorkflow(dto);
    }

    @Operation(summary = "Search workflows")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<Workflow> getLatestWorkflows(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @ParameterObject @Valid @Nullable WorkflowEntityFilter filter,
        @ParameterObject @RequestParam(required = false, defaultValue = "latest") String versions,
        @ParameterObject @PageableDefault(page = 0, size = ApplicationKeys.DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "created", direction = Direction.DESC) }
        ) Pageable pageable
    ) {
        SearchFilter<WorkflowEntity> sf = null;
        if (filter != null) {
            sf = filter.toSearchFilter();
        }
        if ("all".equals(versions)) {
            return workflowService.searchWorkflowsByProject(project, pageable, sf);
        } else {
            return workflowService.searchLatestWorkflowsByProject(project, pageable, sf);
        }
    }

    @Operation(
        summary = "Delete all version of a workflow",
        description = "First check if project exist, then delete a specific workflow version"
    )
    @DeleteMapping(path = "")
    public void deleteAllWorkflow(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @ParameterObject @RequestParam @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String name
    ) {
        workflowService.deleteWorkflows(project, name);
    }

    /*
     * Versions
     */

    @Operation(summary = "Retrieve a specific workflow version given the workflow id")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public Workflow getWorkflowById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Workflow workflow = workflowService.getWorkflow(id);

        //check for project and name match
        if (!workflow.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return workflow;
    }

    @Operation(summary = "Update if exist a workflow in a project context")
    @PutMapping(
        value = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Workflow updateWorkflow(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull Workflow workflowDTO
    ) throws NoSuchEntityException, IllegalArgumentException, SystemException, BindException {
        Workflow workflow = workflowService.getWorkflow(id);

        //check for project and name match
        if (!workflow.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return workflowService.updateWorkflow(id, workflowDTO);
    }

    @Operation(summary = "Delete a specific workflow version, with optional cascade")
    @DeleteMapping(path = "/{id}")
    public void deleteWorkflow(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam(required = false) Boolean cascade
    ) throws NoSuchEntityException {
        Workflow workflow = workflowService.getWorkflow(id);

        //check for project and name match
        if (!workflow.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project or name");
        }

        workflowService.deleteWorkflow(id, cascade);
    }
    
    @Operation(summary = "Get relationships info for a given entity, if available")
    @GetMapping(path = "/{id}/relationships", produces = "application/json; charset=UTF-8")
    public List<RelationshipDetail> getRelationshipsById(
    		@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
    		@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id) throws NoSuchEntityException {
    	Workflow entity = workflowService.getWorkflow(id);

        //check for project and name match
        if ((entity != null) && !entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }
        
        return relationshipsService.getRelationships(id);
    }    
}
