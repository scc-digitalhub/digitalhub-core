/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.metrics.service;

import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.infrastructure.Effect;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import jakarta.annotation.Nullable;
import java.io.Serializable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Metrics finalizer: remove the (user supplied) metrics stored for the entity.
 *
 * Extended by every entity with metrics, annotated as onDeleting effect for its type. A failed removal
 * fails the finalization (CoreRuntimeException). Idempotent. Resource metrics are not touched.
 */
@Slf4j
public abstract class BaseEntityMetricsFinalizer<D extends BaseDTO & SpecDTO & StatusDTO> implements Effect<D> {

    private MetricsService<D> metricsService;

    @Autowired(required = false)
    public void setMetricsService(MetricsService<D> metricsService) {
        this.metricsService = metricsService;
    }

    @Override
    public void process(String stage, D dto, @Nullable Serializable output) {
        if (metricsService == null) {
            return;
        }

        log.debug("cascade delete metrics for {} with id {}", dto.getClass().getSimpleName(), dto.getId());
        try {
            metricsService.deleteMetrics(dto.getId());
        } catch (StoreException | SystemException e) {
            log.error("error deleting metrics for {}: {}", dto.getId(), e.getMessage());
            throw new CoreRuntimeException("error deleting metrics: " + e.getMessage(), e);
        }
    }
}
