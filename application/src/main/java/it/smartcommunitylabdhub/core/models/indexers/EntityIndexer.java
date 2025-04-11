package it.smartcommunitylabdhub.core.models.indexers;

import it.smartcommunitylabdhub.core.models.base.BaseEntity;
import java.util.Collection;

public interface EntityIndexer<T extends BaseEntity> {
    public static final int PAGE_MAX_SIZE = 100;

    public void index(T entity);

    public void indexAll(Collection<T> entities);

    public Collection<IndexField> fields();

    public void clearIndex();

    public void remove(T entity);
}
