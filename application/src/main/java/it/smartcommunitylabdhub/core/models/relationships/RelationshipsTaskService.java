package it.smartcommunitylabdhub.core.models.relationships;

import java.util.List;

import it.smartcommunitylabdhub.commons.models.base.RelationshipDetail;

public interface RelationshipsTaskService {
	public List<RelationshipDetail> getRelationships(String project, String entityId);
}
