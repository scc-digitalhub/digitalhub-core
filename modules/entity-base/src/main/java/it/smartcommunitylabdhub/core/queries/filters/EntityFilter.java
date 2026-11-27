package it.smartcommunitylabdhub.core.queries.filters;

import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;

public interface EntityFilter<D extends BaseDTO> {
    SearchFilter<D> toSearchFilter();
}
