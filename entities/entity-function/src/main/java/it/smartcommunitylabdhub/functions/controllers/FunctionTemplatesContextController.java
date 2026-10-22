package it.smartcommunitylabdhub.functions.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.templates.controllers.BaseEntityTemplateController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/functions")
@PreAuthorize("hasAuthority('ROLE_USER')")
@Validated
@Slf4j
@Tag(name = "Function templates context API", description = "Endpoints related to functions management in project")
public class FunctionTemplatesContextController extends BaseEntityTemplateController<Function> {}
