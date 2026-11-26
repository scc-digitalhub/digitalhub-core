/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package it.smartcommunitylabdhub.lifecycle;

import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.events.EntityAction;
import it.smartcommunitylabdhub.events.EntityOperation;
import it.smartcommunitylabdhub.fsm.exceptions.InvalidTransitionException;
import it.smartcommunitylabdhub.lifecycle.locks.EntityLockManager;
import it.smartcommunitylabdhub.lifecycle.locks.EntityLockManager.EntityLock;
import it.smartcommunitylabdhub.lifecycle.locks.LocalEntityLockManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.Assert;

@Slf4j
public abstract class AbstractLifecycleManager<D extends BaseDTO & SpecDTO & StatusDTO> implements InitializingBean {

    //per-object turns, shared with services and repositories
    protected EntityLockManager lockManager = LocalEntityLockManager.shared();

    protected EntityRepository<D> entityRepository;
    protected ApplicationEventPublisher eventPublisher;

    @Autowired
    public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Autowired
    public void setEntityRepository(EntityRepository<D> entityService) {
        this.entityRepository = entityService;
    }

    @Autowired(required = false)
    public void setLockManager(EntityLockManager lockManager) {
        if (lockManager != null) {
            this.lockManager = lockManager;
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(entityRepository, "entity service is required");
        Assert.notNull(eventPublisher, "event publisher is required");
    }

    /*
     * Locking
     */

    /**
     * Type used as key for the per-object turn: must match the DTO type used by services and repositories
     */
    protected Class<?> getLockType(D dto) {
        return dto.getClass();
    }

    /*
     * Operations
     */

    protected D exec(@NotNull @Valid EntityOperation<D> op, @NotNull Function<D, D> logic)
        throws SystemException, CoreRuntimeException {
        log.debug("handle op {} for id {}", op.getAction(), op.getDto().getId());

        D dto = op.getDto();
        if (log.isTraceEnabled()) {
            log.trace("dto: {}", op.getDto());
        }

        String id = dto.getId();
        Assert.hasText(id, "id can not be null or empty");

        //acquire the turn for this object (re-entrant if already held by the caller)
        try (EntityLock lock = lockManager.acquire(getLockType(dto), id)) {
            //perform logic
            dto = logic.apply(dto);

            //persist to store
            EntityAction action = op.getAction();
            log.debug("perform action {} on id {}", action, id);
            switch (action) {
                case EntityAction.UPDATE:
                    //direct update to repo to avoid lifecycle loops
                    dto = entityRepository.update(id, dto);
                    break;
                case EntityAction.DELETE:
                    //direct delete to repo to avoid lifecycle loops
                    entityRepository.delete(id);
                    break;
                default:
                    //no-op
                    break;
            }

            if (log.isTraceEnabled()) {
                log.trace("Updated dto: {}", dto);
            }
            return dto;
        } catch (InvalidTransitionException e) {
            log.debug("Invalid transition {} -> {}", e.getFromState(), e.getToState());
            throw e;
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage(), e);
        }
    }
}
