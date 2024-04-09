package it.smartcommunitylabdhub.core.controllers.v1.base;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.models.entities.dataitem.DataItem;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.core.ApplicationKeys;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.models.entities.DataItemEntity;
import it.smartcommunitylabdhub.core.models.queries.filters.entities.DataItemEntityFilter;
import it.smartcommunitylabdhub.core.models.queries.services.SearchableDataItemService;
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
@RequestMapping("/dataitems")
//TODO evaluate permissions for project via lookup in dto
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Validated
@Slf4j
@Tag(name = "DataItem base API", description = "Endpoints related to dataitems management out of the Context")
public class DataItemController {

    @Autowired
    SearchableDataItemService dataItemService;

    @Operation(summary = "Create dataItem", description = "Create a dataItem and return")
    @PostMapping(
        value = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public DataItem createDataItem(@RequestBody @Valid @NotNull DataItem dto) throws DuplicatedEntityException {
        return dataItemService.createDataItem(dto);
    }

    @Operation(summary = "List dataItems", description = "Return a list of all dataItems")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<DataItem> getDataItems(
        @ParameterObject @Valid @Nullable DataItemEntityFilter filter,
        @ParameterObject @PageableDefault(page = 0, size = ApplicationKeys.DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "created", direction = Direction.DESC) }
        ) Pageable pageable
    ) {
        SearchFilter<DataItemEntity> sf = null;
        if (filter != null) {
            sf = filter.toSearchFilter();
        }

        return dataItemService.searchDataItems(pageable, sf);
    }

    @Operation(summary = "Get a dataItem by id", description = "Return a dataItem")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public DataItem getDataItem(@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id)
        throws NoSuchEntityException {
        return dataItemService.getDataItem(id);
    }

    @Operation(summary = "Update specific dataItem", description = "Update and return the dataItem")
    @PutMapping(
        path = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public DataItem updateDataItem(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull DataItem dto
    ) throws NoSuchEntityException {
        return dataItemService.updateDataItem(id, dto);
    }

    @Operation(summary = "Delete a dataItem", description = "Delete a specific dataItem")
    @DeleteMapping(path = "/{id}")
    public void deleteDataItem(@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id) {
        dataItemService.deleteDataItem(id);
    }
}
