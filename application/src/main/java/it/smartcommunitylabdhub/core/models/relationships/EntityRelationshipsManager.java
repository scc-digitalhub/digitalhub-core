package it.smartcommunitylabdhub.core.models.relationships;

import it.smartcommunitylabdhub.core.models.base.BaseEntity;
import it.smartcommunitylabdhub.core.models.events.EntityEvent;

public interface EntityRelationshipsManager<T extends BaseEntity> {
	public void handleEvent(EntityEvent<T> event);
}
