package it.smartcommunitylabdhub.core.models.queries.services;

import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.workflow.Workflow;
import it.smartcommunitylabdhub.commons.services.entities.WorkflowService;
import it.smartcommunitylabdhub.core.models.entities.WorkflowEntity;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/*
 * Searchable service for managing workflow
 */
public interface SearchableWorkflowService extends WorkflowService {
    /**
     * List all workflows, with optional filters
     * @param pageable
     * @param filter
     * @return
     */
    Page<Workflow> searchWorkflows(Pageable pageable, @Nullable SearchFilter<WorkflowEntity> filter)
        throws SystemException;

    /**
     * List the latest version of every workflow, with optional filters
     * @param pageable
     * @param filter
     * @return
     */
    Page<Workflow> searchLatestWorkflows(Pageable pageable, @Nullable SearchFilter<WorkflowEntity> filter)
        throws SystemException;

    /**
     * List all versions of every workflow, with optional filters
     * @param project
     * @param pageable
     * @param filter
     * @return
     */
    Page<Workflow> searchWorkflowsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<WorkflowEntity> filter
    ) throws SystemException;
    /**
     * List the latest version of every workflow, with optional filters
     * @param project
     * @param pageable
     * @param filter
     * @return
     */
    Page<Workflow> searchLatestWorkflowsByProject(
        @NotNull String project,
        Pageable pageable,
        @Nullable SearchFilter<WorkflowEntity> filter
    ) throws SystemException;
}
