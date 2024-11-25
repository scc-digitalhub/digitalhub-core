package it.smartcommunitylabdhub.commons.services;

import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.trigger.Trigger;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.validation.BindException;

/*
 * Service for managing triggers
 */
public interface TriggerService {
    /**
     * List all triggers
     * @param pageable
     * @return
     */
    Page<Trigger> listTriggers(Pageable pageable) throws SystemException;

    /**
     * List all triggers for a given user
     * @param user
     * @return
     */
    List<Trigger> listTriggersByUser(@NotNull String user) throws SystemException;

    /**
     * List all triggers for a given project
     * @param project
     * @return
     */
    List<Trigger> listTriggersByProject(@NotNull String project) throws SystemException;

    /**
     * List all triggers for a given task
     * @param taskId
     * @return
     */
    List<Trigger> listTriggersByTaskId(@NotNull String taskId) throws SystemException;

    /**
     * List all triggers for a given project
     * @param project
     * @param pageable
     * @return
     */
    Page<Trigger> listTriggersByProject(@NotNull String project, Pageable pageable) throws SystemException;

    /**
     * Find a specific trigger  via unique ID. Returns null if not found
     * @param id
     * @return
     */
    @Nullable
    Trigger findTrigger(@NotNull String id) throws SystemException;

    /**
     * Get a specific trigger via unique ID. Throws exception if not found
     * @param id
     * @return
     * @throws NoSuchEntityException
     */
    Trigger getTrigger(@NotNull String id) throws NoSuchEntityException, SystemException;

    /**
     * Create a new trigger and store it
     * @param triggerDTO
     * @return
     * @throws DuplicatedEntityException
     */
    Trigger createTrigger(@NotNull Trigger triggerDTO)
        throws DuplicatedEntityException, BindException, IllegalArgumentException, SystemException;

    /**
     * Update a specific trigger
     * @param id
     * @param triggerDTO
     * @return
     * @throws NoSuchEntityException
     */
    Trigger updateTrigger(@NotNull String id, @NotNull Trigger triggerDTO)
        throws NoSuchEntityException, BindException, IllegalArgumentException, SystemException;

    /**
     * Delete a specific trigger via unique ID, with optional cascade
     * @param id
     * @param cascade
     */
    void deleteTrigger(@NotNull String id, @Nullable Boolean cascade) throws SystemException;
}
