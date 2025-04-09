package it.smartcommunitylabdhub.core.controllers.v1.base;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.report.Report;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.core.ApplicationKeys;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.models.entities.ReportEntity;
import it.smartcommunitylabdhub.core.models.queries.filters.entities.ReportEntityFilter;
import it.smartcommunitylabdhub.core.models.queries.services.SearchableReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ApiVersion("v1")
@RequestMapping("/reports")
//TODO evaluate permissions for project via lookup in dto
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Validated
@Slf4j
@Tag(name = "Report base API", description = "Endpoints related to reports management out of the Context")
public class ReportController {

    @Autowired
    SearchableReportService reportService;


    @Operation(summary = "Create report", description = "Create a report and return")
    @PostMapping(
        value = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Report createReport(@RequestBody @Valid @NotNull Report dto)
        throws DuplicatedEntityException, IllegalArgumentException, SystemException, BindException {
        return reportService.createReport(dto);
    }

    @Operation(summary = "List reports", description = "Return a list of all reports")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<Report> getReports(
        @ParameterObject @Valid @Nullable ReportEntityFilter filter,
        @ParameterObject @RequestParam(required = false, defaultValue = "latest") String versions,
        @ParameterObject @PageableDefault(page = 0, size = ApplicationKeys.DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "created", direction = Direction.DESC) }
        ) Pageable pageable
    ) {
        SearchFilter<ReportEntity> sf = null;
        if (filter != null) {
            sf = filter.toSearchFilter();
        }
        if (versions.equals("latest")) {
            return reportService.searchLatestReports(pageable, sf);
        } else {
            return reportService.searchReports(pageable, sf);
        }
    }

    @Operation(summary = "Get a report by id", description = "Return a report")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public Report getReport(@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id)
        throws NoSuchEntityException {
        return reportService.getReport(id);
    }

    @Operation(summary = "Update specific report", description = "Update and return the report")
    @PutMapping(
        path = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Report updateReport(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull Report dto
    ) throws NoSuchEntityException, IllegalArgumentException, SystemException, BindException {
        return reportService.updateReport(id, dto);
    }

    @Operation(summary = "Delete a report", description = "Delete a specific report")
    @DeleteMapping(path = "/{id}")
    public void deleteReport(@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id) {
        reportService.deleteReport(id);
    }

}
