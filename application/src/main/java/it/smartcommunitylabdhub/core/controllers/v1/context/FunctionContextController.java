package it.smartcommunitylabdhub.core.controllers.v1.context;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.base.RelationshipDetail;
import it.smartcommunitylabdhub.commons.models.entities.function.Function;
import it.smartcommunitylabdhub.commons.models.entities.model.Model;
import it.smartcommunitylabdhub.commons.models.entities.task.Task;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.services.entities.TaskService;
import it.smartcommunitylabdhub.core.ApplicationKeys;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.models.entities.FunctionEntity;
import it.smartcommunitylabdhub.core.models.queries.filters.entities.FunctionEntityFilter;
import it.smartcommunitylabdhub.core.models.queries.services.SearchableFunctionService;
import it.smartcommunitylabdhub.core.models.relationships.RelationshipsFunctionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
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

@RestController
@ApiVersion("v1")
@RequestMapping("/-/{project}/functions")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "Function context API", description = "Endpoints related to functions management in project")
public class FunctionContextController {

    @Autowired
    SearchableFunctionService functionService;

    @Autowired
    TaskService taskService;
    
    @Autowired
    RelationshipsFunctionService relationshipsService;

    @Operation(summary = "Create a function in a project context")
    @PostMapping(
        value = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Function createFunction(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @Valid @NotNull @RequestBody Function dto
    ) throws DuplicatedEntityException, SystemException, BindException {
        //enforce project match
        dto.setProject(project);

        //create as new
        return functionService.createFunction(dto);
    }

    @Operation(summary = "Search functions")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<Function> searchFunctions(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @ParameterObject @Valid @Nullable FunctionEntityFilter filter,
        @ParameterObject @RequestParam(required = false, defaultValue = "latest") String versions,
        @ParameterObject @PageableDefault(page = 0, size = ApplicationKeys.DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "created", direction = Direction.DESC) }
        ) Pageable pageable
    ) {
        SearchFilter<FunctionEntity> sf = null;
        if (filter != null) {
            sf = filter.toSearchFilter();
        }

        if ("all".equals(versions)) {
            return functionService.searchFunctionsByProject(project, pageable, sf);
        } else {
            return functionService.searchLatestFunctionsByProject(project, pageable, sf);
        }
    }

    @Operation(summary = "Delete all version of a function")
    @DeleteMapping(path = "")
    public void deleteAllFunction(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @ParameterObject @RequestParam @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String name
    ) {
        functionService.deleteFunctions(project, name);
    }

    /*
     * Versions
     */

    @Operation(summary = "Retrieve a specific function version given the function id")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public Function getFunctionById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Function function = functionService.getFunction(id);

        //check for project and name match
        if (!function.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project ");
        }

        return function;
    }

    @Operation(summary = "Update if exist a function in a project context")
    @PutMapping(
        value = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Function updateFunctionById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull Function functionDTO
    ) throws NoSuchEntityException, IllegalArgumentException, SystemException, BindException {
        Function function = functionService.getFunction(id);

        //check for project and name match
        if (!function.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project ");
        }

        return functionService.updateFunction(id, functionDTO);
    }

    @Operation(summary = "Delete a specific function version, with optional cascade")
    @DeleteMapping(path = "/{id}")
    public void deleteFunctionById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam(required = false) Boolean cascade
    ) throws NoSuchEntityException {
        Function function = functionService.getFunction(id);

        //check for project and name match
        if (!function.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        functionService.deleteFunction(id, cascade);
    }

    /*
     * Tasks
     */

    @Operation(summary = "List tasks for a given function")
    @GetMapping(path = "/{id}/tasks", produces = "application/json; charset=UTF-8")
    public List<Task> getTasks(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Function function = functionService.getFunction(id);

        //check for project and name match
        if (!function.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return taskService.getTasksByFunctionId(id, EntityName.FUNCTION);
    }
    
    @Operation(summary = "Get relationships info for a given entity, if available")
    @GetMapping(path = "/{id}/relationships", produces = "application/json; charset=UTF-8")
    public List<RelationshipDetail> getRelationshipsById(
    		@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
    		@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id) throws NoSuchEntityException {
    	Function entity = functionService.getFunction(id);

        //check for project and name match
        if ((entity != null) && !entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }
        
        return relationshipsService.getRelationships(project, id);
    }
    
}
