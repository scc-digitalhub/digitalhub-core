package it.smartcommunitylabdhub.core.models.indexers;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

public interface SolrPage<T> extends Page<T> {
    Map<String, List<String>> getFilters();
}
