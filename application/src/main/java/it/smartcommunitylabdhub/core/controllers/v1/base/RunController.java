package it.smartcommunitylabdhub.core.controllers.v1.base;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.annotations.validators.ValidateField;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.models.entities.log.Log;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.services.LogService;
import it.smartcommunitylabdhub.commons.services.entities.RunService;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/runs")
@ApiVersion("v1")
@Tag(name = "Run base API", description = "Endpoints related to runs management out of the Context")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class RunController {

    @Autowired
    RunService runService;

    @Autowired
    LogService logService;

    @Operation(summary = "Get a run", description = "Given an uuid return the related Run")
    @GetMapping(path = "/{uuid}", produces = "application/json; charset=UTF-8")
    public ResponseEntity<Run> getRun(@ValidateField @PathVariable(name = "uuid", required = true) String uuid)
        throws NoSuchEntityException {
        return ResponseEntity.ok(this.runService.getRun(uuid));
    }

    @Operation(summary = "Run log list", description = "Return the log list for a specific run")
    @GetMapping(path = "/{uuid}/log", produces = "application/json; charset=UTF-8")
    public ResponseEntity<Page<Log>> getRunLog(
        @ValidateField @PathVariable(name = "uuid", required = true) String uuid,
        Pageable pageable
    ) {
        return ResponseEntity.ok(this.logService.getLogsByRunUuid(uuid, pageable));
    }

    @Operation(summary = "Run list", description = "Return a list of all runs")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public ResponseEntity<Page<Run>> getRuns(@RequestParam Map<String, String> filter, Pageable pageable) {
        return ResponseEntity.ok(this.runService.getRuns(filter, pageable));
    }

    @Operation(summary = "Create and execute a run", description = "Create a run and then execute it")
    @PostMapping(
        path = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public ResponseEntity<Run> createRun(@Valid @RequestBody Run inputRunDTO)
        throws NoSuchEntityException, DuplicatedEntityException {
        return ResponseEntity.ok(this.runService.createRun(inputRunDTO));
    }

    @Operation(summary = "Update specific run", description = "Update and return the update run")
    @PutMapping(
        path = "/{uuid}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public ResponseEntity<Run> updateRun(@Valid @RequestBody Run runDTO, @ValidateField @PathVariable String uuid)
        throws NoSuchEntityException {
        return ResponseEntity.ok(this.runService.updateRun(uuid, runDTO));
    }

    @Operation(summary = "Delete a run", description = "Delete a specific run")
    @DeleteMapping(path = "/{uuid}")
    public ResponseEntity<Boolean> deleteRun(
        @ValidateField @PathVariable(name = "uuid", required = true) String uuid,
        @RequestParam(name = "cascade", defaultValue = "false") Boolean cascade
    ) {
        this.runService.deleteRun(uuid, cascade);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Stop a run", description = "Stop a specific run")
    @PostMapping(
        path = "/{uuid}/stop",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public ResponseEntity<Boolean> stopRun(@ValidateField @PathVariable String uuid) {
        //TODO move to service!
        // Runnable runnable = runnableStoreService.find(uuid);
        //TODO refactor! the framework is responsible for managing runs, not the controller
        // pollingService.stopOne(runnable.getId());

        // Do other operation to stop poller.
        return ResponseEntity.ok(true);
    }
}
