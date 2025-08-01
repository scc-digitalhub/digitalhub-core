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

package it.smartcommunitylabdhub.core.lifecycle;

import it.smartcommunitylabdhub.commons.lifecycle.LifecycleEvent;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

public interface LifecycleManager<D extends BaseDTO & StatusDTO> {
    /*
     * Perform lifecycle operation from events
     */
    public abstract D perform(@NotNull D dto, @NotNull String event);

    public abstract D perform(@NotNull D dto, @NotNull String event, @Nullable LifecycleEvent<D> input);

    /*
     * Handle lifecycle events from state changes
     */
    public abstract D handle(@NotNull D dto, String nexState);

    public abstract D handle(@NotNull D dto, String nexState, @Nullable LifecycleEvent<D> input);
}
