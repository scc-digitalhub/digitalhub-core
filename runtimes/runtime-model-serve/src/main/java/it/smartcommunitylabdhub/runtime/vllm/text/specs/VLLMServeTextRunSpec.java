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

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.runtime.vllm.base.specs.VLLMServeRunSpec;
import it.smartcommunitylabdhub.runtime.vllm.text.VLLMServeTextRuntime;

import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = VLLMServeTextRuntime.RUNTIME, kind = VLLMServeTextRunSpec.KIND, entity = EntityName.RUN)
public class VLLMServeTextRunSpec extends VLLMServeRunSpec {

    public static final String KIND = VLLMServeTextServeTaskSpec.KIND + ":run";

    @JsonSchemaIgnore
    @JsonUnwrapped
    private VLLMServeTextFunctionSpec functionSpec;

    @JsonUnwrapped
    private VLLMServeTextServeTaskSpec taskServeSpec;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        VLLMServeTextRunSpec spec = mapper.convertValue(data, VLLMServeTextRunSpec.class);
        this.functionSpec = spec.getFunctionSpec();
        this.taskServeSpec = spec.getTaskServeSpec();
    }

    public void setFunctionSpec(VLLMServeTextFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }

    public void setTaskServeSpec(VLLMServeTextServeTaskSpec taskServeSpec) {
        this.taskServeSpec = taskServeSpec;
    }

    public static VLLMServeTextRunSpec with(Map<String, Serializable> data) {
        VLLMServeTextRunSpec spec = new VLLMServeTextRunSpec();
        spec.configure(data);
        return spec;
    }
}
