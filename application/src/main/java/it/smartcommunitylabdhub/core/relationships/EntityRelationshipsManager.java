package it.smartcommunitylabdhub.core.relationships;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.core.persistence.BaseEntity;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public interface EntityRelationshipsManager<T extends BaseEntity> {
    void register(@NotNull T entity) throws StoreException;

    void clear(@NotNull T entity) throws StoreException;

    List<RelationshipDetail> getRelationships(@NotNull T entity) throws StoreException;
}
