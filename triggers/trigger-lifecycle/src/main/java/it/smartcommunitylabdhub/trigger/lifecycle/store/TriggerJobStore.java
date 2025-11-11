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
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.function.Predicate;

public interface TriggerJobStore<T extends TriggerJob> {
    void store(@NotNull String id, @NotNull T e) throws StoreException;

    void remove(@NotNull String id) throws StoreException;

    T find(@NotNull String id) throws StoreException;

    List<T> findAll() throws StoreException;
    List<T> findMatching(Predicate<T> filter) throws StoreException;
}
