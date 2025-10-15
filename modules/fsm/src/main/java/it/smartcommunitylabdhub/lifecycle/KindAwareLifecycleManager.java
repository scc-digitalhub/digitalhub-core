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

import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

@Slf4j
public class KindAwareLifecycleManager<D extends BaseDTO & SpecDTO & StatusDTO> implements LifecycleManager<D> {

    protected Map<String, LifecycleManager<D>> managers;

    public KindAwareLifecycleManager() {
        this.managers = new HashMap<>();
    }

    public KindAwareLifecycleManager(Map<String, LifecycleManager<D>> managers) {
        Assert.notEmpty(managers, "lifecycle managers are required");
        this.managers = managers;
    }

    protected String resolve(@NotNull D dto) {
        String kind = dto.getKind();
        if (kind == null) {
            throw new IllegalArgumentException("missing kind in dto " + String.valueOf(dto.getId()));
        }

        return kind;
    }

    protected LifecycleManager<D> getManager(@NotNull String kind) {
        LifecycleManager<D> manager = managers.get(kind);
        if (manager == null) {
            throw new CoreRuntimeException("no lifecycle manager for kind " + kind);
        }

        return manager;
    }

    @Override
    public D perform(@NotNull D dto, @NotNull String event) {
        return getManager(resolve(dto)).perform(dto, event);
    }

    @Override
    public <I> D perform(@NotNull D dto, @NotNull String event, I input) {
        return getManager(resolve(dto)).perform(dto, event, input);
    }

    @Override
    public <I, R> D perform(@NotNull D dto, @NotNull String event, I input, BiConsumer<D, R> effect) {
        return getManager(resolve(dto)).perform(dto, event, input, effect);
    }

    @Override
    public D handle(@NotNull D dto, String nexState) {
        return getManager(resolve(dto)).handle(dto, nexState);
    }

    @Override
    public <I> D handle(@NotNull D dto, String nexState, I input) {
        return getManager(resolve(dto)).handle(dto, nexState, input);
    }
}
