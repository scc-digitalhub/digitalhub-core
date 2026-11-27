/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.core.lifecycle;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.lifecycle.LifecycleManager;
import it.smartcommunitylabdhub.lifecycle.detached.DetachedEntry;
import it.smartcommunitylabdhub.lifecycle.detached.DetachedOperations;
import it.smartcommunitylabdhub.lifecycle.locks.EntityLockManager;
import it.smartcommunitylabdhub.lifecycle.locks.EntityLockManager.EntityLock;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * Detached operations without a live execution in this process move their object to ERROR:
 * - at startup: interrupted by the restart, never re-run (in-memory state is lost), the user can
 *   operate again from ERROR
 * - on expiry of an entry without a live execution
 *
 * The check runs within the object turn: only if still in the detached state, via the primary
 * lifecycle manager of the type (ex. kind aware), re-entering the turn.
 */
@Component
@Slf4j
public class DetachedRecovery implements InitializingBean {

    private final DetachedOperations detachedOperations;
    private final EntityLockManager lockManager;
    private final ApplicationContext applicationContext;

    public DetachedRecovery(
        DetachedOperations detachedOperations,
        EntityLockManager lockManager,
        ApplicationContext applicationContext
    ) {
        Assert.notNull(detachedOperations, "detached operations are required");
        Assert.notNull(lockManager, "lock manager is required");
        Assert.notNull(applicationContext, "application context is required");
        this.detachedOperations = detachedOperations;
        this.lockManager = lockManager;
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        detachedOperations.setOrphanExpiryHandler(entry -> fail(entry, "detached " + entry.state() + " timed out"));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        List<DetachedEntry> entries;
        try {
            entries = detachedOperations.getRegistry().list();
        } catch (RuntimeException e) {
            //never block the startup
            log.error("unable to read detached operations: {}", e.getMessage());
            return;
        }

        for (DetachedEntry entry : entries) {
            if (detachedOperations.isLive(entry.type(), entry.id(), entry.state())) {
                continue;
            }

            log.info("detached {} for {} {} interrupted by restart", entry.state(), entry.type(), entry.id());
            detachedOperations.complete(entry.type(), entry.id(), entry.state());
            fail(entry, "detached " + entry.state() + " interrupted by restart");
        }
    }

    /**
     * Move the object to ERROR, within its turn, only if still in the detached state
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void fail(DetachedEntry entry, String message) {
        try {
            Class<?> clazz = Class.forName(entry.type());
            if (!BaseDTO.class.isAssignableFrom(clazz) || !StatusDTO.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException("unsupported type " + entry.type());
            }

            LifecycleManager manager = (LifecycleManager) applicationContext
                .getBeanProvider(ResolvableType.forClassWithGenerics(LifecycleManager.class, clazz))
                .getIfAvailable();
            EntityRepository repository = (EntityRepository) applicationContext
                .getBeanProvider(ResolvableType.forClassWithGenerics(EntityRepository.class, clazz))
                .getIfAvailable();
            if (manager == null || repository == null) {
                log.error("no lifecycle manager or repository for detached {}, dropped", entry.key());
                return;
            }

            //the turn is keyed by the dto type, as for services, managers and repositories
            try (EntityLock turn = lockManager.acquire(clazz, entry.id())) {
                Object found = repository.find(entry.id());
                if (!(found instanceof StatusDTO dto)) {
                    log.debug("object for detached {} is gone", entry.key());
                    return;
                }

                String state = StatusFieldAccessor.with(dto.getStatus()).getState();
                if (!entry.state().equals(state)) {
                    log.debug("object for detached {} moved to {} meanwhile", entry.key(), state);
                    return;
                }

                dto.setStatus(MapUtils.mergeMultipleMaps(dto.getStatus(), Map.of("message", message)));
                //re-enters the turn: the dto read within it is trusted
                manager.handle((BaseDTO) dto, "ERROR", message);
            }
        } catch (Exception e) {
            log.error("unable to move object of detached {} to ERROR: {}", entry.key(), e.getMessage(), e);
        }
    }
}
