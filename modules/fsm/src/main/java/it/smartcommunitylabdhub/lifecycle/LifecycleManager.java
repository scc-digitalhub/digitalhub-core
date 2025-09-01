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

package it.smartcommunitylabdhub.lifecycle;

import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.function.BiConsumer;

public interface LifecycleManager<D extends BaseDTO & StatusDTO> {
    /*
     * Perform lifecycle operation from events
     */
    public D perform(@NotNull D dto, @NotNull String event);

    public <I> D perform(@NotNull D dto, @NotNull String event, @Nullable I input);

    public <I, R> D perform(
        @NotNull D dto,
        @NotNull String event,
        @Nullable I input,
        @Nullable BiConsumer<D, R> effect
    );

    /*
     * Handle lifecycle events from state changes
     */
    public D handle(@NotNull D dto, String nexState);

    public <I> D handle(@NotNull D dto, String nexState, @Nullable I input);
}
