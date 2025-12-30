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

package it.smartcommunitylabdhub.runtime.vllm.text.specs;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.runtime.vllm.base.specs.VLLMServeFunctionSpec;
import it.smartcommunitylabdhub.runtime.vllm.text.VLLMServeTextRuntime;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = VLLMServeTextRuntime.RUNTIME, kind = VLLMServeTextRuntime.RUNTIME, entity = Function.class)
public class VLLMServeTextFunctionSpec extends VLLMServeFunctionSpec {

    public static VLLMServeTextFunctionSpec with(Map<String, Serializable> data) {
        VLLMServeTextFunctionSpec spec = new VLLMServeTextFunctionSpec();
        spec.configure(data);
        return spec;
    }
}
