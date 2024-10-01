package it.smartcommunitylabdhub.core.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import it.smartcommunitylabdhub.core.models.entities.RelationshipEntity;

public interface RelationshipRepository extends JpaRepository<RelationshipEntity, String>, JpaSpecificationExecutor<RelationshipEntity> {
	List<RelationshipEntity> findBySourceIdOrDestId(String sourceId, String destId);
}
