package it.smartcommunitylabdhub.dataitems.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.dataitems.DataItem;
import it.smartcommunitylabdhub.relationships.controllers.BaseRelationshipsAwareEntityController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/-/{project}/dataitems")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "DataItem extensible context API", description = "Endpoints related to data items management in project")
public class DataItemRelationshipsController extends BaseRelationshipsAwareEntityController<DataItem> {}
