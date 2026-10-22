package it.smartcommunitylabdhub.containerimage.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.containerimage.ContainerImage;
import it.smartcommunitylabdhub.core.controllers.BaseSchemaAwareEntityController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/containerimages")
@PreAuthorize("hasAuthority('ROLE_USER')")
@Validated
@Slf4j
@Tag(
    name = "ContainerImage extensible context API",
    description = "Endpoints related to container images management in project"
)
public class ContainerImageSchemaController extends BaseSchemaAwareEntityController<ContainerImage> {}
