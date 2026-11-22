package it.smartcommunitylabdhub.dataitems.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.dataitems.DataItem;
import it.smartcommunitylabdhub.templates.controllers.BaseEntityTemplateController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dataitems")
@PreAuthorize("hasAuthority('ROLE_USER')")
@Validated
@Slf4j
@Tag(name = "DataItem templates context API", description = "Endpoints related to dataitems management in project")
public class DataItemTemplatesContextController extends BaseEntityTemplateController<DataItem> {}
