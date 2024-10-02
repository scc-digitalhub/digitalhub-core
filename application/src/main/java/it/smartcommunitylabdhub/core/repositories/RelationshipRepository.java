package it.smartcommunitylabdhub.core.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import it.smartcommunitylabdhub.core.models.entities.RelationshipEntity;

public interface RelationshipRepository extends JpaRepository<RelationshipEntity, String>, JpaSpecificationExecutor<RelationshipEntity> {
    @Query("SELECT r FROM RelationshipEntity r WHERE r.project=:project AND (r.sourceId=:entityId OR r.destId=:entityId)")
	List<RelationshipEntity> findByEntityId(String project, String entityId);
}
