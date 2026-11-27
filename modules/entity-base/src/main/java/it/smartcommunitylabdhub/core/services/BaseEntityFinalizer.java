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

package it.smartcommunitylabdhub.core.services;

import it.smartcommunitylabdhub.commons.infrastructure.Effect;
import it.smartcommunitylabdhub.commons.infrastructure.EffectRegistry;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Finalizer driver: runs the "onDeleting" effects registered for the type, in order.
 *
 * Used for objects removed without a lifecycle (not fully managed): best effort, every effect runs
 * and failures (CoreRuntimeException included) are logged. With a lifecycle the same effects run as
 * part of the DELETING step instead.
 *
 * Every entity extends it with its concrete type, to be wired where needed.
 */
@Slf4j
public abstract class BaseEntityFinalizer<D extends BaseDTO & SpecDTO & StatusDTO> implements EntityFinalizer<D> {

    public static final String STAGE = "onDeleting";

    protected EffectRegistry<D> effectRegistry;

    @Autowired(required = false)
    public void setEffectRegistry(EffectRegistry<D> effectRegistry) {
        this.effectRegistry = effectRegistry;
    }

    @Override
    public void finalize(@NotNull D dto) {
        if (effectRegistry == null) {
            return;
        }

        List<Effect<D>> effects = effectRegistry.getEffects(STAGE);
        if (effects == null || effects.isEmpty()) {
            return;
        }

        log.debug("finalize {} with {} effects", dto.getId(), effects.size());
        for (Effect<D> effect : effects) {
            try {
                //each effect works on its own copy
                effect.process(STAGE, copy(dto), null);
            } catch (RuntimeException e) {
                log.error("finalize effect failed for {}: {}", dto.getId(), e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private D copy(D dto) throws IllegalStateException {
        try {
            return JacksonMapper.deepClone(dto, (Class<D>) dto.getClass());
        } catch (IOException e) {
            throw new IllegalStateException("unable to copy " + dto.getId() + ": " + e.getMessage(), e);
        }
    }
}
