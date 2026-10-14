package it.smartcommunitylabdhub.extensions.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.extensions.ExtensionManager;
import it.smartcommunitylabdhub.extensions.model.Extension;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/-/{project}/extensions")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "Extensions context API", description = "Endpoints related to Extensions management in project")
public class ExtensionsController {

    @Autowired
    private ExtensionManager extensionManager;

    @Operation(summary = "Retrieve a specific extension version given the extension id")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public Extension getExtensionById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Extension extension = extensionManager.getExtension(id);

        //check for project and name match
        if (!extension.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return extension;
    }

    @Operation(summary = "Update if exist an extension in a project context")
    @PutMapping(
        value = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Extension updateExtensionById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull Extension extensionDTO
    ) throws NoSuchEntityException, IllegalArgumentException, SystemException, BindException {
        Extension extension = extensionManager.getExtension(id);

        //check for project and name match
        if (!extension.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return extensionManager.updateExtension(id, extensionDTO);
    }

    @Operation(summary = "Delete a specific extension version")
    @DeleteMapping(path = "/{id}")
    public void deleteExtensionById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Extension extension = extensionManager.getExtension(id);

        //check for project and name match
        if (!extension.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        extensionManager.deleteExtension(id);
    }
}
