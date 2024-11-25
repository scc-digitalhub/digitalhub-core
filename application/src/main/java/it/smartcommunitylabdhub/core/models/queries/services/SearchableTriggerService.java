package it.smartcommunitylabdhub.core.models.queries.services;

import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.trigger.Trigger;
import it.smartcommunitylabdhub.commons.services.TriggerService;
import it.smartcommunitylabdhub.core.models.entities.TriggerEntity;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/*
 * Searchable service for managing trigger
 */
public interface SearchableTriggerService extends TriggerService {
    /**
     * List all triggers, with optional filters
     * @param pageable
     * @param filter
     * @return
     */
    Page<Trigger> searchTriggers(Pageable pageable, @Nullable SearchFilter<TriggerEntity> filter)
        throws SystemException;

    /**
     * List the latest version of every trigger, with optional filters
     * @param project
     * @param pageable
     * @param filter
     * @return
     */
    Page<Trigger> searchTriggersByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<TriggerEntity> filter
    ) throws SystemException;
}
