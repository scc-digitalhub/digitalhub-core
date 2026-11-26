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

import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import java.io.Serializable;
import org.springframework.lang.Nullable;

/**
 * Post processors alter the OUTPUT of a lifecycle transition (ex. a runnable), never the dto.
 *
 * They run synchronously within the transition, in order, chained: each one receives (a copy of)
 * the output produced by the previous one and returns the new output, or null to keep it unchanged.
 *
 * Failures: throwing a {@link CoreRuntimeException} FAILS the transition; any other exception is
 * logged and ignored, and the chain continues with the previous output.
 */
@FunctionalInterface
public interface PostProcessor<D extends BaseDTO & SpecDTO & StatusDTO> {
    /**
     * @return the new output, or null to keep the current one. Implementations may narrow the
     *         return type (ex. a specific runnable)
     */
    @Nullable
    Serializable process(String stage, D dto, @Nullable Serializable output) throws CoreRuntimeException;
}
