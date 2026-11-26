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

/**
 * Per-object ordering of operations ("turns").
 *
 * Every operation on a single object (service, lifecycle manager, repository write) runs
 * inside the turn for that object: operations on the same object are executed one at a time,
 * in no specific order. Operations on different objects are independent.
 *
 * Turns are RE-ENTRANT for the thread holding them: a service holding the turn can call the
 * lifecycle manager and the repository for the same object without blocking itself.
 * A turn is bound to the thread which acquired it: work performed on other threads while the
 * turn is held must never try to acquire the same turn.
 *
 * Implementations may be local (single replica) or distributed.
 */
public interface EntityLockManager {
    /**
     * Acquire the turn for the given object, waiting up to the configured timeout.
     *
     * @param type the object type (DTO class)
     * @param id   the object id
     * @return a handle which MUST be closed by the same thread to release the turn
     * @throws EntityLockException if the turn can not be acquired in time or the thread is interrupted
     */
    EntityLock acquire(@NotNull Class<?> type, @NotNull String id) throws EntityLockException;

    /**
     * Check if the current thread holds the turn for the given object
     */
    boolean isHeldByCurrentThread(@NotNull Class<?> type, @NotNull String id);

    /**
     * Handle for an acquired turn, release via close()
     */
    @FunctionalInterface
    interface EntityLock extends AutoCloseable {
        @Override
        void close();
    }
}
