package it.smartcommunitylabdhub.core.repositories;

import it.smartcommunitylabdhub.core.models.entities.TriggerEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TriggerRepository
    extends JpaRepository<TriggerEntity, String>, JpaSpecificationExecutor<TriggerEntity> {
    List<TriggerEntity> findByTask(String task);
}
