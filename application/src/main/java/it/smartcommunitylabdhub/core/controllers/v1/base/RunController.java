package it.smartcommunitylabdhub.core.controllers.v1.base;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.core.ApplicationKeys;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.components.run.RunManager;
import it.smartcommunitylabdhub.core.models.entities.RunEntity;
import it.smartcommunitylabdhub.core.models.queries.filters.entities.RunEntityFilter;
import it.smartcommunitylabdhub.core.models.queries.services.SearchableRunService;
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
import org.springframework.validation.BindException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ApiVersion("v1")
@RequestMapping("/runs")
//TODO evaluate permissions for project via lookup in dto
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Validated
@Slf4j
@Tag(name = "Run base API", description = "Endpoints related to runs management out of the Context")
public class RunController {

    @Autowired
    SearchableRunService runService;

    @Autowired
    RunManager runManager;

    @Operation(summary = "Create run and exec", description = "Create a run and exec")
    @PostMapping(
        value = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Run createRun(@RequestBody @Valid @NotNull Run dto)
        throws DuplicatedEntityException, NoSuchEntityException, SystemException, BindException {
        Run run = runService.createRun(dto);

        // Build the run
        run = runManager.build(run);

        // Run the run
        run = runManager.run(run);

        return run;
    }

    @Operation(summary = "List runs", description = "Return a list of all runs")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<Run> getRuns(
        @ParameterObject @Valid @Nullable RunEntityFilter filter,
        @ParameterObject @PageableDefault(page = 0, size = ApplicationKeys.DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "created", direction = Direction.DESC) }
        ) Pageable pageable
    ) {
        SearchFilter<RunEntity> sf = null;
        if (filter != null) {
            sf = filter.toSearchFilter();
        }

        return runService.searchRuns(pageable, sf);
    }

    @Operation(summary = "Get a run by id", description = "Return a run")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public Run getRun(@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id)
        throws NoSuchEntityException {
        return runService.getRun(id);
    }

    @Operation(summary = "Update specific run", description = "Update and return the run")
    @PutMapping(
        path = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Run updateRun(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull Run dto
    ) throws NoSuchEntityException, IllegalArgumentException, SystemException, BindException {
        return runService.updateRun(id, dto);
    }

    @Operation(summary = "Delete a run", description = "Delete a specific run, with optional cascade on logs")
    @DeleteMapping(path = "/{id}")
    public void deleteRun(@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id) {
        Run run = runService.getRun(id);

        // via manager
        runManager.delete(run);
    }

    @Operation(summary = "Build a specific run")
    @PostMapping(path = "/{id}/build")
    public Run buildRunById(@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id)
        throws NoSuchEntityException {
        Run run = runService.getRun(id);

        // via manager
        return runManager.build(run);
    }

    @Operation(summary = "Execute a specific run")
    @PostMapping(path = "/{id}/run")
    public Run runRunById(@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id)
        throws NoSuchEntityException {
        Run run = runService.getRun(id);

        // via manager
        return runManager.run(run);
    }

    @Operation(summary = "Stop a specific run execution")
    @PostMapping(path = "/{id}/stop")
    public Run stopRunById(@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id)
        throws NoSuchEntityException {
        Run run = runService.getRun(id);

        // via manager
        return runManager.stop(run);
    }

    @Operation(summary = "Resume a specific run execution")
    @PostMapping(path = "/{id}/resume")
    public Run resumeRunById(@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id)
        throws NoSuchEntityException {
        Run run = runService.getRun(id);

        // via manager
        return runManager.resume(run);
    }

    @Operation(summary = "Delete a specific run execution")
    @PostMapping(path = "/{id}/delete")
    public Run deleteRunById(@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id)
        throws NoSuchEntityException {
        Run run = runService.getRun(id);

        // via manager
        return runManager.delete(run);
    }
}
