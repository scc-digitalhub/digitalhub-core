package it.smartcommunitylabdhub.core.controllers.v1.base;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.annotations.validators.ValidateField;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.models.entities.workflow.Workflow;
import it.smartcommunitylabdhub.commons.services.interfaces.WorkflowService;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/workflows")
@ApiVersion("v1")
@Validated
@Tag(name = "Workflow base API", description = "Endpoints related to workflows management out of the Context")
public class WorkflowController {

    @Autowired
    WorkflowService workflowService;

    @Operation(summary = "List workflows", description = "Return a list of all workflows")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public ResponseEntity<Page<Workflow>> getWorkflows(@RequestParam Map<String, String> filter, Pageable pageable) {
        return ResponseEntity.ok(this.workflowService.getWorkflows(filter, pageable));
    }

    @Operation(summary = "Create workflow", description = "Create an workflow and return")
    @PostMapping(
            value = "",
            consumes = {MediaType.APPLICATION_JSON_VALUE, "application/x-yaml"},
            produces = "application/json; charset=UTF-8"
    )
    public ResponseEntity<Workflow> createWorkflow(@Valid @RequestBody Workflow workflowDTO) {
        return ResponseEntity.ok(this.workflowService.createWorkflow(workflowDTO));
    }

    @Operation(summary = "Get an workflow by uuid", description = "Return an workflow")
    @GetMapping(path = "/{uuid}", produces = "application/json; charset=UTF-8")
    public ResponseEntity<Workflow> getWorkflow(
            @ValidateField @PathVariable(name = "uuid", required = true) String uuid
    ) {
        return ResponseEntity.ok(this.workflowService.getWorkflow(uuid));
    }

    @Operation(summary = "Update specific workflow", description = "Update and return the workflow")
    @PutMapping(
            path = "/{uuid}",
            consumes = {MediaType.APPLICATION_JSON_VALUE, "application/x-yaml"},
            produces = "application/json; charset=UTF-8"
    )
    public ResponseEntity<Workflow> updateWorkflow(
            @Valid @RequestBody Workflow workflowDTO,
            @ValidateField @PathVariable String uuid
    ) {
        return ResponseEntity.ok(this.workflowService.updateWorkflow(workflowDTO, uuid));
    }

    @Operation(summary = "Delete an workflow", description = "Delete a specific workflow")
    @DeleteMapping(path = "/{uuid}")
    public ResponseEntity<Boolean> deleteWorkflow(@ValidateField @PathVariable String uuid) {
        return ResponseEntity.ok(this.workflowService.deleteWorkflow(uuid));
    }

    @GetMapping(path = "/{uuid}/runs", produces = "application/json; charset=UTF-8")
    public ResponseEntity<List<Run>> workflowRuns(@ValidateField @PathVariable String uuid) {
        return ResponseEntity.ok(this.workflowService.getWorkflowRuns(uuid));
    }
}
