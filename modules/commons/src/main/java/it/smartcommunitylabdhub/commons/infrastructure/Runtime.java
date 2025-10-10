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

package it.smartcommunitylabdhub.commons.infrastructure;

import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import jakarta.validation.constraints.NotNull;
import org.springframework.lang.Nullable;

/**
 * Runtime expose builder, run and parse method
 */
public interface Runtime<S extends RunBaseSpec, Z extends RunBaseStatus, R extends RunRunnable> {
    S build(@NotNull Run run);

    R run(@NotNull Run run);

    R stop(@NotNull Run run);
    R resume(@NotNull Run run);

    @Nullable
    R delete(@NotNull Run run);

    @Nullable
    default Spec onBuilt(@NotNull Run run) {
        return null;
    }

    @Nullable
    default Z onReady(@NotNull Run run, RunRunnable runnable) {
        return null;
    }

    @Nullable
    default Z onRunning(@NotNull Run run, RunRunnable runnable) {
        return null;
    }

    @Nullable
    default Z onComplete(@NotNull Run run, RunRunnable runnable) {
        return null;
    }

    @Nullable
    default Z onError(@NotNull Run run, RunRunnable runnable) {
        return null;
    }

    @Nullable
    default Z onStopped(@NotNull Run run, RunRunnable runnable) {
        return null;
    }

    @Nullable
    default Z onDeleted(@NotNull Run run, @Nullable RunRunnable runnable) {
        return null;
    }
}
