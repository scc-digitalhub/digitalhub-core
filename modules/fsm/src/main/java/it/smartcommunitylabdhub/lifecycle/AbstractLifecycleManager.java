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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.util.Pair;
import org.springframework.util.Assert;

@Slf4j
public abstract class AbstractLifecycleManager<D extends BaseDTO & SpecDTO & StatusDTO> implements InitializingBean {

    public static final int DEFAULT_TIMEOUT = 30;
    private Map<String, Pair<ReentrantLock, Instant>> locks = new ConcurrentHashMap<>();
    private int timeout = DEFAULT_TIMEOUT;

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

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(entityRepository, "entity service is required");
        Assert.notNull(eventPublisher, "event publisher is required");
    }

    /*
     * Locking
     */
    public void setTimeout(int t) {
        this.timeout = t;
    }

    protected synchronized ReentrantLock getLock(String id) {
        //build lock
        ReentrantLock l = locks.containsKey(id) ? locks.get(id).getFirst() : new ReentrantLock();

        //update last used date
        locks.put(id, Pair.of(l, Instant.now()));

        return l;
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

        //lock and resolve op
        try {
            try {
                //acquire write lock
                getLock(id).tryLock(timeout, TimeUnit.SECONDS);

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
            } catch (StoreException e) {
                log.error("store error: {}", e.getMessage());
                throw new SystemException(e.getMessage());
            } finally {
                getLock(id).unlock();
            }
        } catch (InterruptedException e) {
            throw new SystemException("unable to acquire lock: " + e.getMessage());
        }
    }
}
