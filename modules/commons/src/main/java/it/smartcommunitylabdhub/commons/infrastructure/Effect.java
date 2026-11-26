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
 * Effects are SIDE EFFECTS of a lifecycle transition: they are dispatched asynchronously after the
 * transition has been persisted, receive a copy of the persisted dto and the final output, and
 * produce nothing. When and how they end never affects the transition.
 *
 * To alter the output of a transition use a {@link PostProcessor}.
 */
@FunctionalInterface
public interface Effect<D extends BaseDTO & SpecDTO & StatusDTO> {
    void process(String stage, D dto, @Nullable Serializable output) throws CoreRuntimeException;
}
