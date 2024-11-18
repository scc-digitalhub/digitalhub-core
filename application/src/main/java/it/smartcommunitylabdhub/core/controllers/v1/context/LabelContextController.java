package it.smartcommunitylabdhub.core.controllers.v1.context;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.models.label.Label;
import it.smartcommunitylabdhub.commons.services.entities.LabelService;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ApiVersion("v1")
@RequestMapping("/-/{project}/labels")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "Label CONTEXT API", description = "Endpoints related to labels search")
public class LabelContextController {

    @Autowired
    LabelService labelService;

    @Operation(
        summary = "Search labels",
        description = "Return a list of labels within a project and starting with a specific text"
    )
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<Label> getLabels(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @RequestParam(required = false) String label,
        Pageable pageable
    ) {
        if (StringUtils.hasText(label)) {
            return labelService.searchLabels(project, label.trim(), pageable);
        } else {
            return labelService.findLabelsByProject(project, pageable);
        }
    }
}
