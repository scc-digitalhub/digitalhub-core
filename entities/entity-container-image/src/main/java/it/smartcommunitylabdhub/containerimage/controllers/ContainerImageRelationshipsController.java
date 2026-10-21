package it.smartcommunitylabdhub.containerimage.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.containerimage.ContainerImage;
import it.smartcommunitylabdhub.relationships.controllers.BaseRelationshipsAwareEntityController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/-/{project}/containerimages")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(
    name = "ContainerImage extensible context API",
    description = "Endpoints related to container images management in project"
)
public class ContainerImageRelationshipsController extends BaseRelationshipsAwareEntityController<ContainerImage> {}
