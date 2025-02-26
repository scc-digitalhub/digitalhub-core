package it.smartcommunitylabdhub.core.models.indexers;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class SolrPageImpl<T> extends PageImpl<T> implements SolrPage<T> {

    private static final long serialVersionUID = 8269910872034647723L;

    private final Map<String, List<String>> filters;

    public SolrPageImpl(List<T> content, Pageable pageable, long total, Map<String, List<String>> filters) {
        super(content, pageable, total);
        this.filters = filters;
    }

    @Override
    public Map<String, List<String>> getFilters() {
        return this.filters;
    }
}
