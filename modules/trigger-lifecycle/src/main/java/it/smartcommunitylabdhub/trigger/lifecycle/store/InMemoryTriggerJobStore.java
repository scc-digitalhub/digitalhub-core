/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * Copyright 2025 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.trigger.lifecycle.store;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.triggers.lifecycle.TriggerJob;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

@Slf4j
public class InMemoryTriggerJobStore<T extends TriggerJob> implements TriggerJobStore<T> {

    private final Map<String, T> store = new ConcurrentHashMap<>();

    @Override
    public void store(String id, T e) {
        Assert.hasText(id, "id must not be empty");
        Assert.notNull(e, "trigger job must not be null");

        log.debug("store trigger job {} in memory", id);
        store.put(id, e);
    }

    @Override
    public void remove(String id) {
        Assert.hasText(id, "id must not be empty");

        if (store.containsKey(id)) {
            log.debug("remove trigger job {} from memory", id);
            store.remove(id);
        }
    }

    @Override
    public T find(String id) {
        Assert.hasText(id, "id must not be empty");

        log.debug("find trigger job {} in memory", id);
        return store.get(id);
    }

    @Override
    public List<T> findAll() throws StoreException {
        log.debug("find all trigger jobs in memory");
        return List.copyOf(store.values());
    }

    @Override
    public List<T> findMatching(Predicate<T> filter) throws StoreException {
        if (filter == null) {
            return findAll();
        }

        //apply filter predicate
        log.debug("find all matching trigger jobs in memory");
        return store.values().stream().filter(filter).toList();
    }
}
