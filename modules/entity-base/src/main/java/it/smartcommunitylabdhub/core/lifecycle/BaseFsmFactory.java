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

package it.smartcommunitylabdhub.core.lifecycle;

import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.fsm.AbstractFsmFactory;
import it.smartcommunitylabdhub.fsm.FsmState;
import java.util.Arrays;
import java.util.List;

public class BaseFsmFactory<D extends BaseDTO & StatusDTO> extends AbstractFsmFactory<String, String, D> {

    protected BaseFsmFactory(List<FsmState.Builder<String, String, D>> builders) {
        super(builders);
    }

    @SafeVarargs
    protected BaseFsmFactory(FsmState.Builder<String, String, D>... builders) {
        super(Arrays.asList(builders));
    }
}
