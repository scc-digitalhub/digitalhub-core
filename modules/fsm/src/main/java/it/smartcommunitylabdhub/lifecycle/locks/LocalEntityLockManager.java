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
package it.smartcommunitylabdhub.lifecycle.locks;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

/**
 * Single-replica (JVM local) implementation of per-object turns.
 *
 * One re-entrant lock per object, created on first use and released from memory as soon as
 * no thread holds or waits for it.
 */
@Slf4j
public class LocalEntityLockManager implements EntityLockManager {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    //shared instance used as fallback when no manager is wired:
    //all components must share the same instance to order operations on the same object
    private static final LocalEntityLockManager SHARED = new LocalEntityLockManager();

    public static LocalEntityLockManager shared() {
        return SHARED;
    }

    private final ConcurrentHashMap<String, Entry> locks = new ConcurrentHashMap<>();
    private Duration timeout = DEFAULT_TIMEOUT;

    public LocalEntityLockManager() {}

    public LocalEntityLockManager(Duration timeout) {
        setTimeout(timeout);
    }

    public void setTimeout(Duration timeout) {
        Assert.notNull(timeout, "timeout can not be null");
        Assert.isTrue(!timeout.isNegative(), "timeout can not be negative");
        this.timeout = timeout;
    }

    public Duration getTimeout() {
        return timeout;
    }

    @Override
    public EntityLock acquire(@NotNull Class<?> type, @NotNull String id) throws EntityLockException {
        String key = key(type, id);

        //register interest BEFORE waiting, so the entry is not removed while we wait
        Entry entry = locks.compute(key, (k, e) -> {
            Entry en = e != null ? e : new Entry();
            en.refs++;
            return en;
        });

        boolean acquired;
        try {
            acquired = entry.lock.tryLock(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            release(key, entry);
            throw new EntityLockException("interrupted waiting for turn on " + key, e);
        }

        if (!acquired) {
            Thread holder = entry.lock.owner();
            release(key, entry);

            String holderName = holder != null ? holder.getName() : "unknown";
            log.warn("timeout waiting for turn on {}, held by thread {}", key, holderName);
            throw new EntityLockException("unable to acquire turn on " + key + ", held by " + holderName);
        }

        if (log.isTraceEnabled()) {
            log.trace("acquired turn on {} (hold count {})", key, entry.lock.getHoldCount());
        }

        AtomicBoolean closed = new AtomicBoolean(false);
        return () -> {
            if (closed.compareAndSet(false, true)) {
                //collect deferred actions while still holding the turn (owner-only access)
                List<Runnable> actions = entry.lock.getHoldCount() == 1 ? entry.drain() : List.of();

                entry.lock.unlock();
                release(key, entry);

                if (log.isTraceEnabled()) {
                    log.trace("released turn on {}", key);
                }

                //run outside the turn
                actions.forEach(action -> {
                    try {
                        action.run();
                    } catch (RuntimeException e) {
                        log.error("error running action after release of {}: {}", key, e.getMessage(), e);
                    }
                });
            }
        };
    }

    @Override
    public boolean isHeldByCurrentThread(@NotNull Class<?> type, @NotNull String id) {
        Entry entry = locks.get(key(type, id));
        return entry != null && entry.lock.isHeldByCurrentThread();
    }

    @Override
    public void runAfterRelease(@NotNull Class<?> type, @NotNull String id, @NotNull Runnable action) {
        Assert.notNull(action, "action can not be null");
        Entry entry = locks.get(key(type, id));
        if (entry != null && entry.lock.isHeldByCurrentThread()) {
            //only the owner thread touches the list
            entry.afterRelease.add(action);
        } else {
            action.run();
        }
    }

    /**
     * Number of objects currently tracked (held or waited for)
     */
    public int size() {
        return locks.size();
    }

    private void release(String key, Entry entry) {
        locks.compute(key, (k, e) -> {
            if (e != entry) {
                //should never happen: entries are removed only when unused
                return e;
            }

            e.refs--;
            return e.refs <= 0 ? null : e;
        });
    }

    private static String key(Class<?> type, String id) {
        Assert.notNull(type, "type can not be null");
        Assert.hasText(id, "id can not be null or empty");
        return type.getName() + ":" + id;
    }

    private static final class Entry {

        private final OwnedLock lock = new OwnedLock();
        //number of acquisitions (held + waiting), guarded by map compute
        private int refs = 0;
        //actions deferred to the outermost release, accessed only by the owner thread
        private List<Runnable> afterRelease = new ArrayList<>();

        private List<Runnable> drain() {
            List<Runnable> actions = afterRelease;
            afterRelease = new ArrayList<>();
            return actions;
        }
    }

    private static final class OwnedLock extends ReentrantLock {

        Thread owner() {
            return getOwner();
        }
    }
}
