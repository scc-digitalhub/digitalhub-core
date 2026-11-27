package it.smartcommunitylabdhub.functions.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.extensions.controller.BaseExtensibleEntityController;
import it.smartcommunitylabdhub.functions.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/-/{project}/functions")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "Function extensible context API", description = "Endpoints related to function management in project")
public class FunctionExtensibleContextController extends BaseExtensibleEntityController<Function> {}
